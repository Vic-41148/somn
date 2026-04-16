package dev.vic41148.somn.feature.alarm.captcha

import android.content.Context
import android.content.pm.PackageManager
import dev.vic41148.somn.feature.alarm.captcha.tasks.*

object CaptchaTaskRegistry {
    private val allTasks = mutableMapOf<String, CaptchaTask>()

    init {
        register(MathCaptchaTask())
        register(ShakeCaptchaTask())
        register(SequenceCaptchaTask())
        register(QRCodeCaptchaTask())
        // NFC task placeholder, will check for availability in getTasks()
        register(NFCCaptchaTask())
    }

    private fun register(task: CaptchaTask) {
        allTasks[task.id] = task
    }

    fun getTask(id: String): CaptchaTask? = allTasks[id]

    /**
     * Returns list of available tasks for the current device.
     */
    fun getAvailableTasks(context: Context): List<CaptchaTask> {
        return allTasks.values.filter { task ->
            if (task.id == "nfc") {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
            } else {
                true
            }
        }
    }
}
