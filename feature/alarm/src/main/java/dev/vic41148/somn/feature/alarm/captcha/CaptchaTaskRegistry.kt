package dev.vic41148.somn.feature.alarm.captcha

import android.content.Context
import android.content.pm.PackageManager
import dev.vic41148.somn.feature.alarm.captcha.tasks.*
import java.util.Locale

object CaptchaTaskRegistry {
    private val allTasks = mutableMapOf<String, CaptchaTask>()

    init {
        register(MathCaptchaTask())
        register(ShakeCaptchaTask())
        register(SequenceCaptchaTask())
        register(QRCodeCaptchaTask())
        // NFC captchas only make sense on devices that can read tags. The task is
        // registered unconditionally, but getAvailableTasks() filters it by
        // PackageManager.FEATURE_NFC and resolveTask() falls back to the global
        // preference (or math) when NFC is unavailable — a captcha the user can
        // never solve must not lock them out of dismissing their own alarm.
        register(NFCCaptchaTask())
    }

    private fun register(task: CaptchaTask) {
        allTasks[task.id] = task
    }

    fun getTask(id: String): CaptchaTask? = allTasks[id]

    /**
     * Resolves the captcha task that gates a firing alarm's dismissal.
     *
     * The per-alarm [CaptchaType] (received via [AlarmService.currentCaptchaType], from
     * `Alarm.captchaType`) wins when it names a real task; `NONE` (or an unmappable value) falls
     * back to the global Settings preference so both layers keep working together. A QR task is
     * swapped for math when no QR value has been configured — an unsettable captcha must never
     * lock the user out of dismissing their own alarm.
     *
     * Shared by [dev.vic41148.somn.feature.alarm.ui.AlarmActivity] and the in-app firing screen so
     * the two surfaces can never drift apart on which captcha applies.
     *
     * [nfcAvailable] is the device's NFC capability ([PackageManager.FEATURE_NFC]). An NFC
     * captcha on a device that can't read tags can never be solved, so it falls back to the
     * global preference (and to math if that is itself the NFC task). Both fallbacks are
     * checked on the *final* task — the NFC fallback can land on a QR task, and a QR captcha
     * with no configured value must never lock the user out either.
     */
    fun resolveTask(
        perAlarmType: String,
        globalTaskId: String,
        qrCodeValue: String?,
        nfcAvailable: Boolean = true
    ): CaptchaTask? {
        val taskId = when (perAlarmType.uppercase(Locale.ROOT)) {
            "MATH" -> "math"
            "SHAKE" -> "shake"
            "QR_SCAN" -> "qrcode"
            "NFC" -> "nfc"
            else -> globalTaskId
        }
        var task = getTask(taskId)
        if (task?.id == "nfc" && !nfcAvailable) {
            task = if (globalTaskId != "nfc") getTask(globalTaskId) else getTask("math")
        }
        if (task?.id == "qrcode" && qrCodeValue == null) {
            task = getTask("math")
        }
        task?.reset()
        return task
    }

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
