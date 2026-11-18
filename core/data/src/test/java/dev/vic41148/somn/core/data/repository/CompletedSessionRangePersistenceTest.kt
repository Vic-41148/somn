package dev.vic41148.somn.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.domain.model.SessionRange
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.FilterDuplicateSessionsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Issue #113 end-to-end data-path check: a Sleep as Android row is written through the same
 * [SleepRepository.createSession] + [SleepRepository.completeSession] pair the import uses, then
 * read back through the DAO projection that powers dedupe. The pure use case is covered
 * separately; this proves `endTimeMillis` and `isCompleted` survive persistence, which is the
 * part the filter depends on.
 */
@RunWith(RobolectricTestRunner::class)
class CompletedSessionRangePersistenceTest {

    private lateinit var db: SleepDatabase
    private lateinit var repository: SleepRepository
    private val filterDuplicates = FilterDuplicateSessionsUseCase()

    private val start = 1_700_000_000_000L
    private val end = start + TimeUnit.HOURS.toMillis(8)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, SleepDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SleepRepository(
            db,
            db.sleepSessionDao(),
            db.sleepEpochDao(),
            db.audioEventDao(),
            db.externalVitalsDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun importedSession() = SleepSession(
        startTimeMillis = start,
        endTimeMillis = end,
        isCompleted = true,
        timezoneId = "UTC"
    )

    @Test
    fun importThenReimport_sameFile_isSkippedEntirely() = runTest {
        val session = importedSession()

        val newId = repository.createSession(
            session.startTimeMillis,
            session.timezoneId,
            session.sessionType
        )
        repository.completeSession(session.copy(id = newId))

        val existing = repository.getCompletedSessionRanges()
        assertThat(existing).containsExactly(SessionRange(start, end))

        val secondImport = filterDuplicates(listOf(session), existing)
        assertThat(secondImport.sessions).isEmpty()
        assertThat(secondImport.duplicateCount).isEqualTo(1)
    }

    @Test
    fun incompleteSession_isNotReturnedAsExistingRange() = runTest {
        val session = importedSession()

        repository.createSession(
            session.startTimeMillis,
            session.timezoneId,
            session.sessionType
        )

        assertThat(repository.getCompletedSessionRanges()).isEmpty()

        val import = filterDuplicates(listOf(session), repository.getCompletedSessionRanges())
        assertThat(import.sessions).hasSize(1)
        assertThat(import.duplicateCount).isEqualTo(0)
    }
}
