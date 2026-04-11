package dev.vic41148.somn.core.domain.model

/**
 * Represents a single sleep stage classification.
 */
enum class SleepStage {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    UNKNOWN;

    fun displayName(): String = when (this) {
        AWAKE -> "Awake"
        LIGHT -> "Light Sleep"
        DEEP -> "Deep Sleep"
        REM -> "REM Sleep"
        UNKNOWN -> "Unknown"
    }
}
