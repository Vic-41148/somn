package dev.vic41148.somn.core.data.update

import android.content.ContentValues
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
 * Where the mandatory pre-update backup lives. It is ALWAYS written to app-private storage first
 * (that survives an in-place update, since `adb install -r` keeps app data), then mirrored to a
 * user-visible location - MediaStore Downloads (API 29+) or the legacy public Documents dir
 * (API 26-28) - so there is a copy that survives a full uninstall (the downgrade path).
 *
 * [findLatestPreUpdateBackup] hunts in both places after a reinstall, when app-private storage is
 * gone, to power the "Restore from backup made on [date]?" prompt.
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
     * Copies the freshly-created export zip into app-private storage and a user-visible location.
     * Returns the private file; throws if the private write fails (that is the hard gate). A
     * failure to mirror to the visible location is degraded to a warning via [mirrorFailure].
     */
    suspend fun keepPreUpdateBackup(
        zipFile: File,
        mirrorFailure: (Throwable) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        privateBackupDir.listFiles()?.forEach { it.delete() }
        val privateCopy = File(privateBackupDir, zipFile.name)
        zipFile.copyTo(privateCopy, overwrite = true)
        try {
            materializeUserVisibleCopy(zipFile)
        } catch (e: Exception) {
            mirrorFailure(e)
        }
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

    // ---- user-visible mirror ----

    private fun materializeUserVisibleCopy(zipFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zipFile.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, VISIBLE_DIRECTORY)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore insert failed for ${zipFile.name}")
            try {
                zipFile.inputStream().use { input ->
                    resolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    } ?: throw IllegalStateException("Could not open ${zipFile.name} for writing")
                }
            } finally {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } else {
            val p = publicLegacyDir()
            if (p == null || !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                throw IllegalStateException("External storage unavailable for backup mirror")
            } else {
                p.mkdirs()
                zipFile.copyTo(File(p, zipFile.name), overwrite = true)
            }
        }
    }

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