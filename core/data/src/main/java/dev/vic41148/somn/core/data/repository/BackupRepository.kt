package dev.vic41148.somn.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: SomnPreferencesRepository
) {
    suspend fun performSilentBackup() = withContext(Dispatchers.IO) {
        val backupUriStr = preferencesRepository.backupUri.first() ?: return@withContext
        val uri = Uri.parse(backupUriStr)
        val documentTree = DocumentFile.fromTreeUri(context, uri)
        if (documentTree == null || !documentTree.canWrite()) {
            return@withContext
        }

        // Backup Database
        val dbFile = context.getDatabasePath("somn-database")
        if (dbFile.exists()) {
            copyFileToDocumentTree(dbFile, documentTree, "somn-database.db")
        }

        // Backup Preferences DataStore
        val prefsFile = File(context.filesDir, "datastore/somn_prefs.preferences_pb")
        if (prefsFile.exists()) {
            copyFileToDocumentTree(prefsFile, documentTree, "somn_prefs.preferences_pb")
        }
    }

    private fun copyFileToDocumentTree(sourceFile: File, tree: DocumentFile, destName: String) {
        try {
            var destDoc = tree.findFile(destName)
            if (destDoc == null) {
                destDoc = tree.createFile("application/octet-stream", destName)
            }
            if (destDoc != null) {
                val outputStream = context.contentResolver.openOutputStream(destDoc.uri)
                if (outputStream != null) {
                    val inputStream = sourceFile.inputStream()
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
