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

    private fun isEncryptedSQLite(file: File, passphrase: ByteArray): Boolean {
        return try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
            val hex = passphrase.joinToString("") { "%02x".format(it) }
            val db = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                file.absolutePath, hex.toCharArray(), null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
            )
            db.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
