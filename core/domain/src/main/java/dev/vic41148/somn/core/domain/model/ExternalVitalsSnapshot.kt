package dev.vic41148.somn.core.domain.model

/**
 * Aggregated external vitals a paired wearable wrote into Health Connect during a sleep session.
 * Platform-agnostic — the domain layer does not know these came from Health Connect specifically.
 * They are readings Somn itself does not measure (no HR/SpO2/skin-temp sensor on-device).
 */
data class ExternalVitalsSnapshot(
    val sessionId: Long,
    val avgHeartRateBpm: Float? = null,
    val restingHeartRateBpm: Float? = null,
    val avgHeartRateVariabilityMs: Float? = null,
    val avgSpo2Percent: Float? = null,
    val minSpo2Percent: Float? = null,
    val avgSkinTemperatureCelsius: Float? = null,
    /**
     * Package name of the app that originated the data (e.g. "com.fitbit.FitbitMobile"), when
     * known — this is Health Connect's `dataOrigin.packageName`, not a human-readable label.
     * Resolve to a display name (e.g. "Fitbit") at the UI layer via `PackageManager`. Do not
     * render this raw string directly.
     */
    val sourceApp: String? = null
) {
    val hasAnyData: Boolean get() =
        avgHeartRateBpm != null || avgHeartRateVariabilityMs != null ||
            avgSpo2Percent != null || avgSkinTemperatureCelsius != null
}

/** HEALTH-03: current Health Connect authorization state, re-checked on every sync attempt rather than cached. */
enum class HealthConnectStatus {
    /** Health Connect provider is not installed/available on this device. */
    UNAVAILABLE,
    /** Provider is available but the user has not granted (or has revoked) the permissions Somn needs. */
    NOT_AUTHORIZED,
    /** All required read + write permissions are currently granted. */
    AUTHORIZED
}
