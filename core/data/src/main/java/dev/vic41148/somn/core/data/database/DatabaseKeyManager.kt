package dev.vic41148.somn.core.data.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.backup.EncryptionUtils
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher passphrase for the Room DB. The 256-bit key is generated once via
 * SecureRandom and stored Keystore-wrapped ([EncryptionUtils]) in app-private storage —
 * never in plaintext, never in prefs.
 *
 * Existing v0.1.2 installs carry a plaintext DB: on first open with no stored key,
 * [migratePlaintextIfNeeded] exports it into a fresh encrypted file (SQLCipher
 * `sqlcipher_export`) and deletes the plaintext original plus its WAL sidecars.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: EncryptionUtils
) {

    /**
     * Returns the DB passphrase, generating + persisting it on first use. Also runs the
     * one-time plaintext migration whenever the DB file on disk is still plaintext — including
     * the crash window where a previous run persisted the key but died mid-migration.
     */
    fun getOrCreatePassphrase(): ByteArray {
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
     * which must restore on installs holding a different key. Runs on Room's own open
     * handle, so no key juggling here.
     */
    fun exportDecryptedCopy(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        dest: File
    ) {
        if (dest.exists()) dest.delete()
        db.execSQL("ATTACH DATABASE '${dest.absolutePath}' AS plain KEY '';")
        try {
            db.query("SELECT sqlcipher_export('plain');").use { it.moveToFirst() }
        } finally {
            db.execSQL("DETACH DATABASE plain;")
        }
    }

    /**
     * Imports a validated plaintext staging file as the new live encrypted DB. Verifies the
     * result opens before swapping; the caller closes Room first.
     */
    fun importPlaintextCopy(src: File) {
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        val key = getOrCreatePassphrase()
        val hex = key.joinToString("") { "%02x".format(it) }
        val target = dbFile()
        val tmp = File(target.parent, "${target.name}.importing")
        if (tmp.exists()) tmp.delete()
        val plain = net.sqlcipher.database.SQLiteDatabase.openDatabase(
            src.absolutePath, "", null,
            net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
        ) ?: error("Cannot open restore candidate.")
        try {
            plain.rawExecSQL("ATTACH DATABASE '${tmp.absolutePath}' AS encrypted KEY \"x'$hex'\";")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            plain.rawExecSQL("DETACH DATABASE encrypted;")
        } finally {
            plain.close()
        }
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
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
                val hex = key.joinToString("") { "%02x".format(it) }
                val db = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                    file.absolutePath, "", null,
                    net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
                ) ?: return false
                try {
                    db.rawExecSQL("PRAGMA key = \"x'$hex'\";")
                    val ok = db.rawQuery("PRAGMA integrity_check", null)?.use { c ->
                        c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                    } ?: false
                    ok && cleanSchema(
                        db.rawQuery("SELECT type, name FROM sqlite_master", null)?.use { c ->
                            buildList {
                                while (c.moveToNext()) add(c.getString(0) to c.getString(1))
                            }
                        } ?: emptyList()
                    )
                } finally {
                    db.close()
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Rejects triggers, views, and unknown tables outright — a restore candidate must be a
     * plain data file matching our schema, never executable SQL objects we did not create.
     */
    private fun cleanSchema(objects: List<Pair<String, String>>): Boolean {
        return objects.all { (type, name) ->
            when (type) {
                "table", "index" -> name in knownTables || name in systemObjects ||
                    name.startsWith("sqlite_") || name.startsWith("room_")
                else -> false
            }
        }
    }

    /**
     * One-time upgrade for installs that predate encryption: exports a plaintext DB into a
     * fresh encrypted file (SQLCipher `sqlcipher_export`), swaps it in, deletes the plaintext
     * original and its -wal/-shm/-journal sidecars. No-op on fresh installs (no DB file) and
     * on already-encrypted installs.
     */
    private fun migratePlaintextIfNeeded(passphrase: ByteArray) {
        val db = dbFile()
        if (!db.exists()) return
        if (!isPlaintextSQLite(db)) return
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        val tmp = File(db.parent, "${db.name}.migrating")
        if (tmp.exists()) tmp.delete()
        var plain: net.sqlcipher.database.SQLiteDatabase? = null
        try {
            plain = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                db.absolutePath, "", null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE
            )
            val hex = passphrase.joinToString("") { "%02x".format(it) }
            plain.rawExecSQL(
                "ATTACH DATABASE '${tmp.absolutePath}' AS encrypted KEY \"x'$hex'\";"
            )
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            plain.rawExecSQL("DETACH DATABASE encrypted;")
        } finally {
            plain?.close()
        }
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

    private fun isPlaintextSQLite(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        return header.toString(Charsets.US_ASCII).startsWith("SQLite format 3\u0000")
    }
    /** True when [file] is a SQLCipher database that [passphrase] opens. */
    fun isEncryptedSQLite(file: File, passphrase: ByteArray): Boolean {
        return try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
            val hex = passphrase.joinToString("") { "%02x".format(it) }
            val db = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                file.absolutePath, "", null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
            ) ?: return false
            try {
                db.rawExecSQL("PRAGMA key = \"x'$hex'\";")
                db.rawQuery("SELECT count(*) FROM sqlite_master", null)?.use {
                    it.moveToFirst()
                }
                true
            } finally {
                db.close()
            }
        } catch (_: Exception) {
            false
        }
    }
}
