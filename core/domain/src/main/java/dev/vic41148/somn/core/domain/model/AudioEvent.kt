package dev.vic41148.somn.core.domain.model

enum class AudioEventType {
    SNORE, COUGH, TALK, ANOMALY
}

data class AudioEvent(
    val id: Long = 0,
    val sessionId: Long,
    val timestampMillis: Long,
    val durationSeconds: Int,
    val type: AudioEventType,
    val intensityDecibels: Int,
    val clipPath: String? = null
)
