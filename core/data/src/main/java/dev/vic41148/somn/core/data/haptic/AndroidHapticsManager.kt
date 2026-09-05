package dev.vic41148.somn.core.data.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.haptic.HapticsIntensity
import dev.vic41148.somn.core.domain.haptic.HapticsManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Raw-[android.os.Vibrator] implementation of [HapticsManager].
 *
 * This is the no-touch-gesture path: export/backup/NAS completions, wind-down finishes, and the
 * live-touch fallback when a Compose `HapticFeedbackType` has no equivalent constant (e.g. tick).
 * Because it bypasses the system's automatic touch-feedback gating, it must double-check the app's
 * [SomnPreferencesRepository.hapticsEnabled] preference itself - done here by caching both the
 * on/off switch and the [HapticsIntensity] from DataStore once at start and keeping them live, so
 * every effect method reads current values synchronously with no per-call suspension.
 *
 * Effects are calibrated from the "Light / Standard / Strong" amplitude scale (~80/150/255) and the
 * Android visual haptics principle that meaningful state changes get the energy while frequent
 * low-stakes events stay subtle. Rejected-on-purpose is a double-tick ("no"), never a confirm.
 * Durations err on the longer side: the silent-vibration trap on OEM motors is a single sub-50ms
 * blip at low amplitude, which rings as nothing through a case.
 */
@Singleton
class AndroidHapticsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    preferencesRepository: SomnPreferencesRepository
) : HapticsManager {

    companion object {
        private const val TAG = "SomnHaptics"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _enabled = MutableStateFlow(true)
    private val _intensity = MutableStateFlow(HapticsIntensity.STANDARD)

    override val enabled: Boolean get() = _enabled.value
    override val intensity: HapticsIntensity get() = _intensity.value

    init {
        // Keep the cached values in step with the DataStore so a settings change is reflected
        // immediately and survives process restarts without call sites reading flows.
        scope.launch { preferencesRepository.hapticsEnabled.collect { _enabled.value = it } }
        scope.launch {
            preferencesRepository.hapticsIntensity.collect { _intensity.value = it }
        }
    }

    override fun tick() {
        if (!enabled) return
        vibrate(VibrationEffect.createOneShot(30, amplitude()))
    }

    override fun confirm() {
        if (!enabled) return
        vibrate(VibrationEffect.createOneShot(60, amplitude()))
    }

    override fun reject() {
        if (!enabled) return
        // Two bumps with a gap - a clear "no", distinct from the single confirm click. Durations
        // kept generous so the double-buzz reads on a linear motor rather than blurring into one.
        vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 25, 60, 30),
                intArrayOf(0, amplitude(), 0, amplitude()),
                -1
            )
        )
    }

    override fun longPress() {
        if (!enabled) return
        vibrate(VibrationEffect.createOneShot(80, amplitude()))
    }

    override fun backgroundComplete(gentle: Boolean) {
        if (!enabled) return
        if (gentle) {
            // Ease-in envelope for relax-state finishes (wind-down ending): four pulses ramp up
            // from barely-there, so someone mid-breathing-exercise is not startled by a hard buzz.
            vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 70, 70, 70, 70),
                    intArrayOf(0, 40, 90, 150, 220),
                    -1
                )
            )
        } else {
            // Higher-energy "you're done" - double pulse. Predefined where the API exists so the
            // OEM's own double-click is used rather than a homemade approximation.
            val doubleClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 30, 40),
                    intArrayOf(0, 255, 0, 255),
                    -1
                )
            }
            vibrate(doubleClick)
        }
    }

    override fun preview() {
        if (!enabled) return
        // The preview is the only effect the user actively seeks out - it must be unambiguous.
        // A single <50ms blip can pass unnoticed on OEM linear motors, so use two pulses for every
        // level, with the standard tier keeping decent separation between them.
        when (intensity) {
            HapticsIntensity.LIGHT -> vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 40, 30),
                    intArrayOf(0, 110, 0, 110),
                    -1
                )
            )
            HapticsIntensity.STANDARD -> vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 60, 50, 60),
                    intArrayOf(0, 180, 0, 180),
                    -1
                )
            )
            HapticsIntensity.STRONG -> vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 70, 40, 70, 40, 50),
                    intArrayOf(0, 255, 0, 255, 0, 220),
                    -1
                )
            )
        }
    }

    private fun amplitude(): Int = when (intensity) {
        HapticsIntensity.LIGHT -> 80
        HapticsIntensity.STANDARD -> 150
        HapticsIntensity.STRONG -> 255
    }

    private fun vibrator(): Vibrator? {
        // Preferred: the system's single default vibrator behind the VIBRATOR_MANAGER service
        // (Android 12+). Fall back to the legacy service if a ROM returns a null manager or default
        // unit - belt and suspenders across OEM builds.
        val managerVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val legacyVibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        return managerVibrator ?: legacyVibrator
    }

    private fun vibrate(effect: VibrationEffect) {
        val vibrator = vibrator()
        if (vibrator == null) {
            Log.w(TAG, "vibrate() no-op: no Vibrator service available")
            return
        }
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "vibrate() no-op: device reports no vibrator")
            return
        }
        Log.d(TAG, "vibrate(): effect=$effect enabled=$enabled intensity=$intensity")
        runCatching { vibrator.vibrate(effect) }
            .onFailure { Log.e(TAG, "vibrate() failed", it) }
    }
}