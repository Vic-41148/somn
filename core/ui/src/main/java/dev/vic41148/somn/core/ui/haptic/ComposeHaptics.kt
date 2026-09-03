package dev.vic41148.somn.core.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.vic41148.somn.core.domain.haptic.HapticsIntensity
import dev.vic41148.somn.core.domain.haptic.HapticsManager

/**
 * Composition-local handle to the app-wide [HapticsManager]. Provided once at the activity root via
 * [ProvideHaptics] with the injectable implementation; screens read `LocalHaptics.current` and call
 * `tick()` / `confirm()` / etc. without ever touching a [android.os.Vibrator] or a Compose
 * `HapticFeedbackType` directly.
 */
val LocalHaptics: androidx.compose.runtime.ProvidableCompositionLocal<HapticsManager> =
    staticCompositionLocalOf {
        error("LocalHaptics not provided - wrap the app in ProvideHaptics() with an injected HapticsManager")
    }

/**
 * Provides [LocalHaptics] for this subtree, backed by [delegate] but routing the live-touch
 * confirmations and long-presses through the platform's own haptic-feedback path so the OEM haptic
 * engine decides the feel (and the system respects the user's touch-feedback setting automatically).
 * "Strong" intensity layers a raw pulse from the delegate on top so it still reads firmer.
 */
@Composable
fun ProvideHaptics(delegate: HapticsManager, content: @Composable () -> Unit) {
    val composeFeedback = LocalHapticFeedback.current
    val composeHaptics = androidx.compose.runtime.remember(delegate, composeFeedback) {
        ComposeHaptics(delegate, composeFeedback)
    }
    CompositionLocalProvider(LocalHaptics provides composeHaptics, content = content)
}

/**
 * Thin Compose adapter over an injected [HapticsManager]. Touch-confirmation and long-press effects
 * go through [HapticFeedback] (the compose rendering of `View.performHapticFeedback`); everything
 * else (ticks, rejects, background completions, previews) delegates to the raw-vibrator
 * implementation because Compose exposes no constant for them.
 */
internal class ComposeHaptics(
    private val delegate: HapticsManager,
    private val composeFeedback: HapticFeedback
) : HapticsManager {

    override val enabled: Boolean get() = delegate.enabled
    override val intensity: HapticsIntensity get() = delegate.intensity

    override fun tick() = delegate.tick()

    override fun confirm() {
        if (!delegate.enabled) return
        composeFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        // Strong layers a raw pulse so the confirmation still lands even on engines with a quiet
        // standard confirm; Light/Standard ride the platform path alone.
        if (delegate.intensity == HapticsIntensity.STRONG) delegate.confirm()
    }

    override fun reject() = delegate.reject()

    override fun longPress() {
        if (!delegate.enabled) return
        composeFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    override fun backgroundComplete(gentle: Boolean) = delegate.backgroundComplete(gentle)

    override fun preview() = delegate.preview()
}