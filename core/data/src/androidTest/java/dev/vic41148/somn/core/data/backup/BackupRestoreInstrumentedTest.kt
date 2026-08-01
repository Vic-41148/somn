package dev.vic41148.somn.core.data.backup

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.data.database.ALL_MIGRATIONS
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.data.database.entity.SleepSessionEntity
import dev.vic41148.somn.core.data.repository.BackupRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device coverage for the backup/restore path. Robolectric does not reproduce real SQLite WAL
 * behaviour or the Android Keystore faithfully enough to trust here, and both are load-bearing:
 * the WAL is why a naive file copy loses the most recent night, and the Keystore is why the old
 * NAS payload could never be restored anywhere.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val portableCrypto = PortableCrypto()
    private val passphrase = "TEST-PASS-PHRASE-0001"

    private lateinit var database: SleepDatabase
    private lateinit var repository: BackupRepository

    private val markerStart = 1_753_900_000_000L

    @Before
    fun setUp() {
        deleteDatabaseFiles()
        database = openDatabase()
        repository = BackupRepository(
            context = context,
            preferencesRepository = SomnPreferencesRepository(context, EncryptionUtils()),
            database = database,
            portableCrypto = portableCrypto
        )
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        deleteDatabaseFiles()
        context.cacheDir.listFiles()?.filter { it.name.startsWith("backup-test") }?.forEach { it.delete() }
    }

    // ---- The filename regression ----

    @Test
    fun databaseFileResolvesToTheFileRoomActuallyWrote() = runBlocking {
        insertMarkerSession()

        // The original defect: backups read getDatabasePath("somn-database"), which never existed,
        // so the exists() guard was always false and the DB was silently skipped every time.
        val resolved = repository.databaseFile()

        assertThat(resolved.name).isEqualTo(SleepDatabase.DATABASE_NAME)
        assertThat(resolved.exists()).isTrue()
        assertThat(resolved.length()).isGreaterThan(0L)
        assertThat(File(context.getDatabasePath("somn-database").path).exists()).isFalse()
    }

    // ---- The write-ahead log ----

    @Test
    fun checkpointMakesRecentWritesVisibleInTheMainDatabaseFile() = runBlocking {
        insertMarkerSession()

        repository.checkpointWal()

        // Read the raw file the way a restore would — not through the live Room handle, which would
        // happily serve rows still sitting in -wal and hide the whole problem.
        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(1)
    }

    // ---- Full round trip ----

    @Test
    fun encryptedBackupRestoresTheDataItCaptured() = runBlocking {
        insertMarkerSession()
        val backup = writeEncryptedBackup()

        // Wipe the live data so a passing assertion can only come from the restored file.
        database.clearAllTables()
        repository.checkpointWal()
        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(0)

        val result = repository.restoreDatabase(Uri.fromFile(backup), passphrase)

        assertThat(result).isInstanceOf(BackupRepository.RestoreResult.SuccessRestartRequired::class.java)
        // Checked before reading any rows: opening the restored file re-creates -shm, so asserting
        // this after a query would only prove that SQLite works.
        assertThat(File("${repository.databaseFile().path}-wal").exists()).isFalse()
        assertThat(File("${repository.databaseFile().path}-shm").exists()).isFalse()

        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(1)
    }

    @Test
    fun restoreRejectsTheWrongPassphraseWithoutTouchingTheDatabase() = runBlocking {
        insertMarkerSession()
        val backup = writeEncryptedBackup()

        val result = repository.restoreDatabase(Uri.fromFile(backup), "NOT-THE-PASSPHRASE")

        assertThat(result).isInstanceOf(BackupRepository.RestoreResult.Failure::class.java)
        // The live database must survive a failed restore intact.
        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(1)
    }

    @Test
    fun restoreRefusesAKeystoreEncryptedPayload() = runBlocking {
        insertMarkerSession()
        repository.checkpointWal()

        // Exactly what the old NasSyncWorker uploaded: a real Keystore-encrypted blob, produced by
        // the real hardware-backed key on this device.
        val legacy = File(context.cacheDir, "backup-test-legacy.enc")
        legacy.writeBytes(EncryptionUtils().encryptBytes(repository.databaseFile().readBytes()))

        val result = repository.restoreDatabase(Uri.fromFile(legacy), passphrase)

        assertThat(result).isInstanceOf(BackupRepository.RestoreResult.Failure::class.java)
        val message = (result as BackupRepository.RestoreResult.Failure).message
        // Must name the real cause. Telling the user to check their recovery key would send them
        // retyping a key that can never work on a device-bound payload.
        assertThat(message).contains("device-bound")
        assertThat(message).doesNotContain("recovery passphrase")
        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(1)
    }

    @Test
    fun restoreRejectsAFileThatDecryptsToSomethingOtherThanADatabase() = runBlocking {
        insertMarkerSession()

        val notADatabase = File(context.cacheDir, "backup-test-junk.enc")
        val kek = portableCrypto.deriveKek(passphrase.toCharArray())
        notADatabase.writeBytes(portableCrypto.encrypt("definitely not SQLite".toByteArray(), kek))

        val result = repository.restoreDatabase(Uri.fromFile(notADatabase), passphrase)

        assertThat(result).isInstanceOf(BackupRepository.RestoreResult.Failure::class.java)
        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(1)
    }

    @Test
    fun plaintextBackupsRestoreWithoutAPassphrase() = runBlocking {
        insertMarkerSession()
        repository.checkpointWal()

        // The path existing users are on until they set a recovery key.
        val plain = File(context.cacheDir, "backup-test-plain.db")
        repository.databaseFile().copyTo(plain, overwrite = true)
        database.clearAllTables()
        repository.checkpointWal()

        val result = repository.restoreDatabase(Uri.fromFile(plain), passphrase = null)

        assertThat(result).isInstanceOf(BackupRepository.RestoreResult.SuccessRestartRequired::class.java)
        assertThat(markerRowsIn(repository.databaseFile())).isEqualTo(1)
    }

    // ---- Helpers ----

    private fun openDatabase(): SleepDatabase =
        Room.databaseBuilder(context, SleepDatabase::class.java, SleepDatabase.DATABASE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    private suspend fun insertMarkerSession() {
        database.sleepSessionDao().insert(
            SleepSessionEntity(
                startTimeMillis = markerStart,
                endTimeMillis = markerStart + 28_800_000L,
                sleepDurationMinutes = 465,
                isCompleted = true
            )
        )
    }

    /** Counts marker rows by opening the file directly, bypassing Room's cache and the WAL. */
    private fun markerRowsIn(file: File): Int {
        if (!file.exists()) return 0
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return db.use {
            it.rawQuery(
                "SELECT COUNT(*) FROM sleep_sessions WHERE startTimeMillis = ?",
                arrayOf(markerStart.toString())
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        }
    }

    private fun writeEncryptedBackup(): File {
        repository.checkpointWal()
        val kek = portableCrypto.deriveKek(passphrase.toCharArray())
        val backup = File(context.cacheDir, "backup-test-db.enc")
        repository.databaseFile().inputStream().use { input ->
            backup.outputStream().use { output -> portableCrypto.encrypt(input, output, kek) }
        }
        return backup
    }

    private fun deleteDatabaseFiles() {
        val base = context.getDatabasePath(SleepDatabase.DATABASE_NAME)
        listOf(base, File("${base.path}-wal"), File("${base.path}-shm")).forEach { it.delete() }
    }
}
