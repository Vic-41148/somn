package dev.vic41148.somn.core.data.update

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the mandatory pre-update backup lives: app-private storage only (that survives an
 * in-place update, since `adb install -r` keeps app data). A previous version also mirrored
 * the plaintext zip to public Downloads; that mirror is gone — no plaintext backup lands in
 * shared storage anymore. [findLatestPreUpdateBackup] still reads such older visible copies
 * once, so a reinstall can recover data written before this change.
 *
 * [findLatestPreUpdateBackup] powers the "Restore from backup made on [date]?" prompt.
 */
@Singleton
class UpdateBackupStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** A located backup: display [name] and a readable [file] materialized on the device. */
    data class BackupRef(val name: String, val file: File)

    private val privateBackupDir: File
        get() = File(context.filesDir, "update_backups").apply { mkdirs() }

    /**
     * Copies the freshly-created export zip into app-private storage. Returns the private
     * file; throws if the private write fails (that is the hard gate).
     */
    suspend fun keepPreUpdateBackup(zipFile: File): File = withContext(Dispatchers.IO) {
        privateBackupDir.listFiles()?.forEach { it.delete() }
        val privateCopy = File(privateBackupDir, zipFile.name)
        zipFile.copyTo(privateCopy, overwrite = true)
        privateCopy
    }

    /** Locates the most recent pre-update backup: app-private first, then user-visible history. */
    suspend fun findLatestPreUpdateBackup(): BackupRef? = withContext(Dispatchers.IO) {
        val private = privateBackupDir.listFiles()
            ?.filter { it.exists() }
            ?.maxByOrNull { it.lastModified() }
        if (private != null) return@withContext BackupRef(private.name, private)

        val visible = findLatestVisible()
        if (visible != null) return@withContext visible

        null
    }

    // ---- read-only lookup of pre-change visible copies ----

    private fun findLatestVisible(): BackupRef? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_ADDED
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val args = arrayOf(VISIBLE_DIRECTORY, "$VISIBLE_DIRECTORY%")
            val sort = "${MediaStore.Downloads.DATE_ADDED} DESC"

            var candidate: String? = null
            var candidateDate = Long.MIN_VALUE
            resolver.query(collection, projection, selection, args, sort)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED))
                    if (name.startsWith(EXPORT_PREFIX) && date >= candidateDate) {
                        candidate = name
                        candidateDate = date
                    }
                }
            }
            if (candidate == null) return null
            val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val where = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val foundUri = resolver.query(uri, arrayOf(MediaStore.Downloads._ID), where, arrayOf(candidate), null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    android.content.ContentUris.withAppendedId(uri, id)
                } else null
            }
            if (foundUri == null) return null
            val cache = File(context.cacheDir, candidate)
            resolver.openInputStream(foundUri)?.use { input ->
                cache.outputStream().use { output -> input.copyTo(output) }
            }
            if (!cache.exists()) return null
            return BackupRef(candidate, cache)
        } else {
            val p = publicLegacyDir() ?: return null
            val files = p.listFiles()?.filter { it.name.startsWith(EXPORT_PREFIX) }?.toList() ?: return null
            if (files.isEmpty()) return null
            val newest = files.maxByOrNull { it.lastModified() } ?: return null
            return BackupRef(newest.name, newest)
        }
    }

    private fun publicLegacyDir(): File? {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) ?: return null
        return File(base, "Somn/update_backups")
    }

    private companion object {
        const val VISIBLE_DIRECTORY = "Download/Somn/"
        const val EXPORT_PREFIX = "somn-export-"
    }
}

/** Timestamped export filename shared by the manual export and the mandatory pre-update backup. */
fun preUpdateBackupFileName(now: Long = System.currentTimeMillis()): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
    return "somn-export-$stamp.zip"
}