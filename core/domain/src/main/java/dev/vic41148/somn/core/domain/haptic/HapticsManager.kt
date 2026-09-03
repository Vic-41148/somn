package dev.vic41148.somn.core.domain.haptic

/**
 * User-facing haptic strength. Scales raw-vibrator amplitude (Light ~80, Standard ~150,
 * Strong ~255) and, on the Compose touch path, decides whether a raw pulse is layered on top
 * of the system haptic so "Strong" still reads stronger when the OEM engine is quiet.
 */
enum class HapticsIntensity {
    LIGHT,
    STANDARD,
    STRONG
}

/**
 * Single app-wide gate in front of every vibration this app fires.
 *
 * There are two families of feedback and one interface for both, so call sites in Compose
 * screens, ViewModels, and Workers never touch [android.view.Vibrator] or a Compose
 * `HapticFeedbackType` directly - they just say what happened:
 *
 *  - Live-touch effects (toggles, segments, slider steps, confirmations) ride the system's
 *    touch-feedback path where possible so the platform and OEM haptic engine decide the feel,
 *    and
 *  - Background completions (export/backup/NAS/wind-down) that have no touch gesture to hang a
 *    haptic off of fall back to the raw [android.os.Vibrator] path with an explicit intensity.
 *
 * Every method is a no-op when [enabled] is false, so the per-call guard is handled in one place.
 */
interface HapticsManager {
    /** App-level haptics switch, cached from the DataStore preference. */
    val enabled: Boolean

    /** Current user-selected [HapticsIntensity]. */
    val intensity: HapticsIntensity

    /** Light tick for toggles, segment selection, slider steps, and small selections. */
    fun tick()

    /** "It worked" - confirmation of a successful action tied to a live touch. */
    fun confirm()

    /** Distinct "no" feel for wrong answers / validation failures, never the same as [confirm]. */
    fun reject()

    /** Long-press weight feedback for alarm actions and similar firm presses. */
    fun longPress()

    /**
     * Completion of a background task the user is waiting on but not touching - export, backup,
     * NAS sync finishing. Set [gentle] for relax-state finishes (wind-down exercise ending) where
     * an ease-in envelope beats an abrupt buzz.
     */
    fun backgroundComplete(gentle: Boolean = false)

    /** Settings preview: fires the current [intensity]'s representative effect so the user can feel it. */
    fun preview()
}