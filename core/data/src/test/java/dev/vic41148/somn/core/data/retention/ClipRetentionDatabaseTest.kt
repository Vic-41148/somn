package dev.vic41148.somn.core.data.retention

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.data.database.dao.AudioEventDao
import dev.vic41148.somn.core.data.database.entity.AudioEventEntity
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.AudioEventType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Exercises clip retention against a real database and real files on disk, because the promise in
 * PRIVACY.md is about both: the WAV has to actually leave the filesystem, and the row's clipPath
 * has to stop pointing at it.
 */
@RunWith(RobolectricTestRunner::class)
class ClipRetentionDatabaseTest {

    private lateinit var db: SleepDatabase
    private lateinit var audioEventDao: AudioEventDao
    private lateinit var repository: SleepRepository
    private lateinit var clipDir: File

    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SleepDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioEventDao = db.audioEventDao()
        repository = SleepRepository(
            db,
            db.sleepSessionDao(),
            db.sleepEpochDao(),
            audioEventDao,
            db.externalVitalsDao()
        )
        clipDir = File(context.filesDir, "sleep_talk").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        clipDir.deleteRecursively()
    }

    private fun writeClip(name: String): File =
        File(clipDir, name).apply { writeBytes(ByteArray(64)) }

    private suspend fun insertClipEvent(
        ageDays: Long,
        clip: File?,
        syncedToNas: Boolean = false
    ): Long = audioEventDao.insert(
        AudioEventEntity(
            sessionId = 1L,
            timestampMillis = now - TimeUnit.DAYS.toMillis(ageDays),
            durationSeconds = 4,
            type = AudioEventType.TALK.name,
            intensityDecibels = 42,
            clipPath = clip?.absolutePath,
            syncedToNas = syncedToNas
        )
    )

    @Test
    fun `only clips older than the cutoff are selected for pruning`() = runTest {
        val oldId = insertClipEvent(ageDays = 10, clip = writeClip("old.wav"))
        insertClipEvent(ageDays = 2, clip = writeClip("recent.wav"))

        val cutoff = ClipRetentionPolicy.cutoffMillis(now, retentionDays = 7)!!
        val expired = audioEventDao.getEventsWithClipsOlderThan(cutoff)

        assertThat(expired.map { it.id }).containsExactly(oldId)
    }

    @Test
    fun `un-synced clips are pruned too once they age out`() = runTest {
        // Retention beats backup convenience: if NAS sync hasn't managed to upload a recording
        // within the window, the recording still goes.
        val id = insertClipEvent(ageDays = 30, clip = writeClip("never-synced.wav"), syncedToNas = false)

        val cutoff = ClipRetentionPolicy.cutoffMillis(now, retentionDays = 7)!!

        assertThat(audioEventDao.getEventsWithClipsOlderThan(cutoff).map { it.id }).containsExactly(id)
    }

    @Test
    fun `events whose clip was already cleared are not selected again`() = runTest {
        val id = insertClipEvent(ageDays = 30, clip = null)

        val cutoff = ClipRetentionPolicy.cutoffMillis(now, retentionDays = 7)!!
        val expired = audioEventDao.getEventsWithClipsOlderThan(cutoff)

        assertThat(expired.map { it.id }).doesNotContain(id)
    }

    @Test
    fun `deleteAllAudioClips removes the files and forgets their paths`() = runTest {
        val first = writeClip("first.wav")
        val second = writeClip("second.wav")
        insertClipEvent(ageDays = 1, clip = first)
        insertClipEvent(ageDays = 40, clip = second)

        val deleted = repository.deleteAllAudioClips()

        assertThat(deleted).isEqualTo(2)
        assertThat(first.exists()).isFalse()
        assertThat(second.exists()).isFalse()
        assertThat(audioEventDao.getEventsWithClips()).isEmpty()
    }

    @Test
    fun `deleteAllAudioClips keeps the events themselves in history`() = runTest {
        insertClipEvent(ageDays = 1, clip = writeClip("kept-event.wav"))

        repository.deleteAllAudioClips()

        // Only the audio goes — the night's history must still show that talking happened.
        assertThat(audioEventDao.getBySession(1L)).hasSize(1)
    }

    @Test
    fun `deleteAllAudioClips clears the path even when the file is already gone`() = runTest {
        val clip = writeClip("vanished.wav")
        insertClipEvent(ageDays = 1, clip = clip)
        clip.delete()

        val deleted = repository.deleteAllAudioClips()

        // Nothing was removed from disk, but a dangling clipPath would leave playback UI
        // offering a file that cannot be opened.
        assertThat(deleted).isEqualTo(0)
        assertThat(audioEventDao.getEventsWithClips()).isEmpty()
    }
}
