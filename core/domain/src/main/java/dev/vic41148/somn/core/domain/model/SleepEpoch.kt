package dev.vic41148.somn.core.domain.model

/**
 * A single epoch (30-second window) of sleep data.
 */
data class SleepEpoch(
    val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val stage: SleepStage,
    val movementMagnitude: Float,
    val movementVariability: Float = 0f
)
