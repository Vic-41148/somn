package dev.vic41148.somn.core.data.backup

import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dev.vic41148.somn.core.domain.usecase.ExportJsonUseCase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the "Export all data" bundle (full-fidelity JSON + flat CSV) as a ZIP written to
 * [targetFile]. Shared by the Manual export share sheet and the mandatory pre-update backup so
 * both produce byte-identical archives - there is exactly one definition of "a complete export".
 */
@Singleton
class CreateExportZipUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val exportCsv: ExportCsvUseCase,
    private val exportJson: ExportJsonUseCase
) {

    suspend fun create(targetFile: File): File {
        val sessions = sleepRepository.getRecentSessions(EXPORT_SESSION_LIMIT)
        val csv = exportCsv(sessions)
        val json = exportJson(sessions)

        targetFile.outputStream().use { rawOut ->
            ZipOutputStream(rawOut).use { zip ->
                zip.putNextEntry(ZipEntry(CSV_ENTRY_NAME))
                zip.write(csv.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(JSON_ENTRY_NAME))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return targetFile
    }

    companion object {
        const val CSV_ENTRY_NAME = "somn_export.csv"
        const val JSON_ENTRY_NAME = "somn_export.json"
        const val EXPORT_SESSION_LIMIT = 1000
    }
}