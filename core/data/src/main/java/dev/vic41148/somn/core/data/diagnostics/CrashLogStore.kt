package dev.vic41148.somn.core.data.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Zero-telemetry crash capture: the uncaught-exception handler writes a redacted stack
 * trace to app-private storage and nothing else. Nothing leaves the device on its own —
 * the user copies it into a GitHub issue deliberately, via Settings → About.
 */
object CrashLogStore {

    private const val DIR = "crash-logs"
    private const val MAX_LOGS = 5

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(context, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun latest(context: Context): File? =
        logDir(context).listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()

    fun readLatest(context: Context): String? =
        latest(context)?.takeIf { it.exists() }?.readText()

    fun writeCrashLog(context: Context, throwable: Throwable): File {
        val dir = logDir(context).apply { mkdirs() }
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS - 1)
            ?.forEach { it.delete() }
        val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        file.writeText(redact(buildString {
            append("Somn crash log — paste into a GitHub issue if you report this.\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n")
            append("App: ${appVersion(context)}\n\n")
            append(sw.toString())
        }))
        return file
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    }.getOrDefault("unknown")

    /**
     * Stack traces sometimes embed absolute paths (clip files, DB paths) in exception
     * messages. Fold app-private and shared-storage prefixes down to a placeholder —
     * the frames that matter for diagnosis survive.
     */
    internal fun redact(text: String): String =
        text.replace(Regex("/data/[^\\s()]*"), "/data/<app>")
            .replace(Regex("/storage/[^\\s()]*"), "/storage/<shared>")

    private fun logDir(context: Context): File = File(context.filesDir, DIR)
}
