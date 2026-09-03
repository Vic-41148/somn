package dev.vic41148.somn.core.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Controller to trigger a gentle nudge when snoring is detected.
 */
class SnoringNudgeController(private val context: Context) {

    companion object {
        /**
         * Continuous snoring produces a new classified SNORE event roughly once per breath
         * (every few seconds) - nudge() had no rate limiting at all, so a single snoring bout
         * would vibrate the phone almost continuously all night instead of the intended
         * occasional "gentle nudge." A cooldown gives each nudge a real chance to work (person
         * shifts position/quiets down) before nudging again if snoring is still ongoing.
         */
        private const val MIN_INTERVAL_MS = 3 * 60 * 1000L
    }

    @Volatile private var lastNudgeMillis = 0L

    fun nudge() {
        val now = System.currentTimeMillis()
        if (now - lastNudgeMillis < MIN_INTERVAL_MS) return
        lastNudgeMillis = now

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Gentle double-tap vibration
        val pattern = longArrayOf(0, 200, 200, 200)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
