package dev.vic41148.somn.core.domain.model

/**
 * Determines which movement sensor is used as primary input for sleep stage classification.
 */
enum class TrackingMode {
    /** Phone accelerometer — requires phone on bed. Low battery usage. */
    ACCELEROMETER,
    /** Ultrasonic sonar — contactless, phone on nightstand. Higher battery usage. Beta. */
    SONAR
}
