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
    fun nudge() {
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
