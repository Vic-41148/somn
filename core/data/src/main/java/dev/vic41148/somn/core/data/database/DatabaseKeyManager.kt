package dev.vic41148.somn.core.data.database

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.backup.EncryptionUtils
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Owns the SQLCipher passphrase for the Room DB. The 256-bit key is generated once via
 * SecureRandom and stored Keystore-wrapped ([EncryptionUtils]) in app-private storage —
 * never in plaintext, never in prefs.
 *
 * Existing v0.1.2 installs carry a plaintext DB: on first open with no stored key,
 * [migratePlaintextIfNeeded] copies it table-by-table into a fresh encrypted file and
 * deletes the plaintext original plus its WAL sidecars.
 *
 * Implementation note: sqlcipher-android executes every statement on a pooled connection,
 * so per-connection state (ATTACH, PRAGMA key) never survives to the next call. All
 * cross-database work here is therefore done as explicit schema reads plus batched row
 * copies — never ATTACH — and every direct handle goes through [SupportOpenHelperFactory],
 * which keys each pooled connection via hook. Verified on-device; see ExportProbeTest history.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: EncryptionUtils
) {
    companion object {
        private const val TAG = "DatabaseKeyManager"
        private const val COPY_BATCH_ROWS = 500
    }

    /**
     * Returns the DB passphrase, generating + persisting it on first use. Also runs the
     * one-time plaintext migration whenever the DB file on disk is still plaintext — including
     * the crash window where a previous run persisted the key but died mid-migration.
     */
    fun getOrCreatePassphrase(): ByteArray {
        // Room opens through SupportOpenHelperFactory right after this returns, so the
        // native library must already be loaded on every path, not just migration ones.
        loadNative()
        val keyFile = keyFile()
        val key = if (keyFile.exists()) {
            encryption.decryptBytes(keyFile.readBytes())
        } else {
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            // Persist before first use so a crash never strands an encrypted DB whose key
            // was lost; a leftover plaintext DB is re-migrated on the next launch.
            keyFile.writeBytes(encryption.encryptBytes(fresh))
            fresh
        }
        migratePlaintextIfNeeded(key)
        val db = dbFile()
        if (db.exists() && !isPlaintextSQLite(db) && !isEncryptedSQLite(db, key)) {
            // Encrypted DB whose key is lost (e.g. data restored without the key file):
            // unrecoverable by definition. Quarantine it and start fresh rather than
            // crash-looping on open.
            db.renameTo(File(db.parent, "${db.name}.unreadable-${System.currentTimeMillis()}"))
            keyFile().delete()
            return getOrCreatePassphrase()
        }
        return key
    }

    private fun keyFile(): File = File(context.filesDir, "db_key.bin")

    private fun dbFile(): File = context.getDatabasePath(SleepDatabase.DATABASE_NAME)

    /**
     * Loads the SQLCipher native library once. The new `sqlcipher-android` API loads via
     * plain `System.loadLibrary` (no `loadLibs(Context)`), which must not run twice.
     */
    private val nativeLoaded = AtomicBoolean(false)

    private fun loadNative() {
        if (nativeLoaded.compareAndSet(false, true)) {
            System.loadLibrary("sqlcipher")
        }
    }

    /** Tables a restore candidate may contain, besides Room/SQLite system objects. */
    val knownTables: Set<String> = setOf(
        "alarm_events", "alarms", "audio_events", "external_vitals", "habit_logs",
        "session_tags", "sleep_epochs", "sleep_sessions", "tags", "user_profile"
    )

    private val systemObjects: Set<String> = setOf(
        "android_metadata", "sqlite_sequence", "room_master_table"
    )

    /**
     * Exports the live encrypted DB to a plaintext file for portable (passphrase) backups,
     * which must restore on installs holding a different key. Reads through Room's own open
     * handle and writes with framework SQLite — no ATTACH involved.
     */
    fun exportDecryptedCopy(
        db: SupportSQLiteDatabase,
        dest: File
    ) {
        if (dest.exists()) dest.delete()
        val destDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            dest.absolutePath, null
        )
        try {
            destDb.beginTransaction()
            try {
                copySchemaAndRows(
                    query = { db.query(it) },
                    execDest = { destDb.execSQL(it) },
                    insertBatch = { table, columns, batch ->
                        insertBatchFramework(destDb, table, columns, batch)
                    },
                    getUserVersion = { readUserVersion { db.query(it) } },
                    setUserVersion = { destDb.execSQL("PRAGMA user_version = $it") }
                )
                destDb.setTransactionSuccessful()
            } finally {
                destDb.endTransaction()
            }
        } finally {
            destDb.close()
        }
    }

    /**
     * Imports a validated plaintext staging file as the new live encrypted DB. Verifies the
     * result opens before swapping; the caller closes Room first.
     */
    fun importPlaintextCopy(src: File) {
        loadNative()
        val key = getOrCreatePassphrase()
        val target = dbFile()
        val tmp = File(target.parent, "${target.name}.importing")
        if (tmp.exists()) tmp.delete()
        copyPlainToEncrypted(src, key, tmp)
        if (!isEncryptedSQLite(tmp, key)) {
            tmp.delete()
            error("Import produced no readable encrypted database.")
        }
        tmp.renameTo(target)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(target.parent, "${target.name}$suffix").delete()
        }
    }

    /** True when [file] opens with [key] (null = plaintext attempt) and is structurally sound. */
    fun passesIntegrityCheck(file: File, key: ByteArray?): Boolean {
        return try {
            if (key == null) {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    file.absolutePath, null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                try {
                    val ok = db.rawQuery("PRAGMA integrity_check", null).use {
                        it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true)
                    }
                    ok && cleanSchema(
                        db.rawQuery("SELECT type, name FROM sqlite_master", null).use { c ->
                            buildList {
                                while (c.moveToNext()) add(c.getString(0) to c.getString(1))
                            }
                        }
                    )
                } finally {
                    db.close()
                }
            } else {
                useKeyedDatabase(file.absolutePath, key) { db ->
                    val ok = db.query("PRAGMA integrity_check").use {
                        it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true)
                    }
                    ok && cleanSchema(
                        db.query("SELECT type, name FROM sqlite_master").use { c ->
                            buildList {
                                while (c.moveToNext()) add(c.getString(0) to c.getString(1))
                            }
                        }
                    )
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rejects triggers, views, and unknown tables outright — a restore candidate must be a
     * plain data file matching our schema, never executable SQL objects we did not create.
     * Room-generated indices (`index_<table>_<columns>`) are data-less and always allowed.
     */
    private fun cleanSchema(objects: List<Pair<String, String>>): Boolean {
        return objects.all { (type, name) ->
            when (type) {
                "table" -> name in knownTables || name in systemObjects ||
                    name.startsWith("sqlite_") || name.startsWith("room_")
                "index" -> name.startsWith("index_") || name.startsWith("sqlite_")
                else -> false
            }
        }
    }

    /**
     * One-time upgrade for installs that predate encryption: copies a plaintext DB into a
     * fresh encrypted file, swaps it in, deletes the plaintext original and its -wal/-shm
     * sidecars. No-op on fresh installs (no DB file) and on already-encrypted installs.
     */
    private fun migratePlaintextIfNeeded(passphrase: ByteArray) {
        val db = dbFile()
        if (!db.exists()) return
        if (!isPlaintextSQLite(db)) return
        loadNative()
        val tmp = File(db.parent, "${db.name}.migrating")
        if (tmp.exists()) tmp.delete()
        copyPlainToEncrypted(db, passphrase, tmp)
        if (!tmp.exists() || !isEncryptedSQLite(tmp, passphrase)) {
            tmp.delete()
            error("Plaintext DB migration produced no encrypted output; original kept.")
        }
        db.delete()
        tmp.renameTo(db)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(db.parent, "${db.name}$suffix").delete()
        }
    }

    /**
     * Copies a plaintext SQLite file into a fresh SQLCipher file keyed by [key]: schema
     * (tables, then indices) plus rows in 500-row pages, plus the user_version Room
     * validates against. Shared by migration and restore-import.
     */
    private fun copyPlainToEncrypted(src: File, key: ByteArray, dest: File) {
        val started = android.os.SystemClock.elapsedRealtime()
        val srcDb = android.database.sqlite.SQLiteDatabase.openDatabase(
            src.absolutePath, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        // The callback version matches the source so the fresh file is stamped with the
        // schema version Room validates against — never a placeholder.
        val srcVersion = readUserVersion { srcDb.rawQuery(it, null) }
        keyedHelper(dest.absolutePath, key, srcVersion).use { helper ->
            val destDb = helper.writableDatabase
            // One transaction: without it every row is its own fsync (minutes on big
            // histories); atomicity here is a bonus, the file is verified after anyway.
            destDb.beginTransaction()
            try {
                copySchemaAndRows(
                    query = { srcDb.rawQuery(it, null) },
                    execDest = { destDb.execSQL(it) },
                    insertBatch = { table, columns, batch ->
                        insertBatchSupport(destDb, table, columns, batch)
                    },
                    getUserVersion = { readUserVersion { srcDb.rawQuery(it, null) } },
                    setUserVersion = { destDb.execSQL("PRAGMA user_version = $it") }
                )
                destDb.setTransactionSuccessful()
            } finally {
                destDb.endTransaction()
                srcDb.close()
            }
        }
        Log.i(TAG, "Plaintext copy took ${android.os.SystemClock.elapsedRealtime() - started}ms")
    }

    /**
     * Handle for creating a fresh encrypted file: the callback version matches the
     * plaintext source's user_version (read before copying), so the new file is stamped
     * with the schema version Room validates against. Only ever used on files that do
     * not exist yet — verify paths use [useKeyedDatabase] instead.
     */
    private fun keyedHelper(path: String, key: ByteArray, version: Int): SupportSQLiteOpenHelper {
        loadNative()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(path)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return SupportOpenHelperFactory(key).create(config)
    }

    /**
     * Runs [block] on a keyed handle and restores the file's original user_version
     * afterwards. Opening with a fixed callback version would otherwise stamp it —
     * the framework writes the callback version after onUpgrade/onDowngrade — and
     * Room would then refuse to migrate a version-skewed file it could have handled.
     * Files already at [SleepDatabase.SCHEMA_VERSION] take no callbacks and are untouched.
     */
    private fun <R> useKeyedDatabase(
        path: String,
        key: ByteArray,
        block: (SupportSQLiteDatabase) -> R
    ): R {
        var seenVersion: Int? = null
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(path)
            .callback(object : SupportSQLiteOpenHelper.Callback(SleepDatabase.SCHEMA_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    seenVersion = 0
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    seenVersion = oldVersion
                }
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    seenVersion = oldVersion
                }
            })
            .build()
        loadNative()
        SupportOpenHelperFactory(key).create(config).use { helper ->
            val db = helper.writableDatabase
            try {
                return block(db)
            } finally {
                seenVersion?.let { original ->
                    if (original != 0 && original != SleepDatabase.SCHEMA_VERSION) {
                        runCatching { db.execSQL("PRAGMA user_version = $original") }
                    }
                }
            }
        }
    }

    /**
     * Schema (tables, then indices) plus all rows in [COPY_BATCH_ROWS]-row pages, plus the
     * user_version. Source and destination stay abstract because the four call sites mix
     * framework and SQLCipher handles in both directions — all cursors surface as
     * [android.database.Cursor] either way.
     */
    private fun copySchemaAndRows(
        query: (String) -> Cursor,
        execDest: (String) -> Unit,
        insertBatch: (table: String, columns: List<String>, batch: List<List<Any?>>) -> Unit,
        getUserVersion: () -> Int,
        setUserVersion: (Int) -> Unit
    ) {
        val schema = mutableListOf<Triple<String, String, String>>()
        query("SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY rowid").use { c ->
            while (c.moveToNext()) schema.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
        }
        schema.filter { it.first != "table" && it.first != "index" }
            .forEach { Log.w(TAG, "Skipping non-data object during copy: ${it.first} ${it.second}") }
        // sqlite_sequence and friends are internal bookkeeping recreated on demand — replaying
        // their CREATE fails, and sequences restart correctly from the copied rows anyway.
        val tables = schema.filter { it.first == "table" && !it.second.startsWith("sqlite_") }
        (tables.map { it.third } + schema.filter { it.first == "index" }.map { it.third })
            .forEach { execDest(it) }
        schema.filter { it.first == "table" && !it.second.startsWith("sqlite_") }
            .map { it.second }.forEach { table ->
            var offset = 0
            while (true) {
                val batch = mutableListOf<List<Any?>>()
                var columns: List<String> = emptyList()
                query("SELECT * FROM \"$table\" ORDER BY rowid LIMIT $COPY_BATCH_ROWS OFFSET $offset").use { c ->
                    columns = c.columnNames.toList()
                    while (c.moveToNext()) {
                        batch.add((0 until c.columnCount).map { i ->
                            when {
                                c.isNull(i) -> null
                                else -> when (c.getType(i)) {
                                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                                    else -> c.getBlob(i)
                                }
                            }
                        })
                    }
                }
                if (batch.isEmpty()) break
                insertBatch(table, columns, batch)
                if (batch.size < COPY_BATCH_ROWS) break
                offset += batch.size
            }
        }
        setUserVersion(getUserVersion())
    }

    private fun insertStatement(table: String, columns: List<String>): String {
        val cols = columns.joinToString(", ") { "\"$it\"" }
        val placeholders = columns.joinToString(", ") { "?" }
        return "INSERT INTO \"$table\" ($cols) VALUES ($placeholders)"
    }

    private fun insertBatchFramework(
        destDb: android.database.sqlite.SQLiteDatabase,
        table: String,
        columns: List<String>,
        batch: List<List<Any?>>
    ) {
        val stmt = destDb.compileStatement(insertStatement(table, columns))
        try {
            batch.forEach { row ->
                stmt.clearBindings()
                row.forEachIndexed { i, value ->
                    val b = i + 1
                    when (value) {
                        null -> stmt.bindNull(b)
                        is Long -> stmt.bindLong(b, value)
                        is Double -> stmt.bindDouble(b, value)
                        is String -> stmt.bindString(b, value)
                        is ByteArray -> stmt.bindBlob(b, value)
                        else -> stmt.bindString(b, value.toString())
                    }
                }
                stmt.executeInsert()
            }
        } finally {
            stmt.close()
        }
    }

    private fun insertBatchSupport(
        destDb: SupportSQLiteDatabase,
        table: String,
        columns: List<String>,
        batch: List<List<Any?>>
    ) {
        val stmt = destDb.compileStatement(insertStatement(table, columns))
        try {
            batch.forEach { row ->
                stmt.clearBindings()
                row.forEachIndexed { i, value ->
                    val b = i + 1
                    when (value) {
                        null -> stmt.bindNull(b)
                        is Long -> stmt.bindLong(b, value)
                        is Double -> stmt.bindDouble(b, value)
                        is String -> stmt.bindString(b, value)
                        is ByteArray -> stmt.bindBlob(b, value)
                        else -> stmt.bindString(b, value.toString())
                    }
                }
                stmt.execute()
            }
        } finally {
            stmt.close()
        }
    }

    private fun readUserVersion(query: (String) -> Cursor): Int =
        query("PRAGMA user_version").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun isPlaintextSQLite(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        return header.toString(Charsets.US_ASCII).startsWith("SQLite format 3\u0000")
    }

    /** True when [file] is a SQLCipher database that [passphrase] opens. */
    fun isEncryptedSQLite(file: File, passphrase: ByteArray): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            useKeyedDatabase(file.absolutePath, passphrase) { db ->
                db.query("SELECT count(*) FROM sqlite_master").use {
                    it.moveToFirst()
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
