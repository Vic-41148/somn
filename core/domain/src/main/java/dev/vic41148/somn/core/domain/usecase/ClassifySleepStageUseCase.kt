package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SleepStage

/**
 * Classifies accelerometer epoch data into sleep stages.
 *
 * Pipeline:
 * 1. Calculate movement magnitude (RMS of acceleration minus gravity)
 * 2. Calculate variability within the epoch window
 * 3. Apply thresholds to classify stage
 * 4. Returns raw classification (smoothing applied separately)
 */
class ClassifySleepStageUseCase {

    companion object {
        // Thresholds calibrated for phone accelerometer at ~10Hz
        private const val AWAKE_THRESHOLD = 0.15f         // High movement → awake
        private const val DEEP_THRESHOLD = 0.05f           // Very low movement → deep sleep
        private const val DEEP_VARIABILITY_MAX = 0.03f     // Very low variability for deep
        private const val REM_MAGNITUDE_MAX = 0.10f        // Below awake threshold
        private const val REM_VARIABILITY_MIN = 0.02f      // Moderate variability (twitches)
        private const val REM_VARIABILITY_MAX = 0.08f      // But not full awake-level variability
    }

    /**
     * Classify a single epoch based on movement data.
     *
     * @param movementMagnitude RMS of (acc - gravity) over the 30s window
     * @param movementVariability Standard deviation of magnitude samples in the window
     * @return Classified sleep stage
     */
    operator fun invoke(
        movementMagnitude: Float,
        movementVariability: Float
    ): SleepStage {
        return when {
            // High movement → awake
            movementMagnitude >= AWAKE_THRESHOLD -> SleepStage.AWAKE
            // Very still + very low variability → deep sleep (SWS)
            movementMagnitude <= DEEP_THRESHOLD &&
                movementVariability <= DEEP_VARIABILITY_MAX -> SleepStage.DEEP
            // Low movement + moderate variability → REM (atonia with occasional twitches)
            movementMagnitude <= REM_MAGNITUDE_MAX &&
                movementVariability in REM_VARIABILITY_MIN..REM_VARIABILITY_MAX -> SleepStage.REM
            // Everything else below awake → light sleep
            else -> SleepStage.LIGHT
        }
    }

    /**
     * Apply median filter smoothing across a window of stages.
     * Uses a 3-epoch sliding window to remove noise.
     */
    fun smoothStages(stages: List<SleepStage>): List<SleepStage> {
        if (stages.size < 3) return stages

        return stages.mapIndexed { index, _ ->
            if (index == 0 || index == stages.lastIndex) {
                stages[index]
            } else {
                val window = listOf(stages[index - 1], stages[index], stages[index + 1])
                medianStage(window)
            }
        }
    }

    private fun medianStage(window: List<SleepStage>): SleepStage {
        // Find most common stage in window (mode as proxy for median on ordinal)
        return window.groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: SleepStage.UNKNOWN
    }
}
