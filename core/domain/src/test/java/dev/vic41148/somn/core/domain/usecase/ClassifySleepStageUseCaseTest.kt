package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SleepStage
import org.junit.Test

class ClassifySleepStageUseCaseTest {

    private val useCase = ClassifySleepStageUseCase()

    // ---- invoke() — single-epoch classification ----

    @Test
    fun invoke_highMovement_classifiesAwake() {
        assertThat(useCase(movementMagnitude = 0.15f, movementVariability = 0.0f))
            .isEqualTo(SleepStage.AWAKE)
    }

    @Test
    fun invoke_awakeThreshold_takesPriorityOverVariability() {
        // Even with variability that would otherwise look like deep/REM, high magnitude wins.
        assertThat(useCase(movementMagnitude = 0.5f, movementVariability = 0.0f))
            .isEqualTo(SleepStage.AWAKE)
    }

    @Test
    fun invoke_veryStillAndLowVariability_classifiesDeep() {
        assertThat(useCase(movementMagnitude = 0.05f, movementVariability = 0.03f))
            .isEqualTo(SleepStage.DEEP)
    }

    @Test
    fun invoke_lowMovementModerateVariability_classifiesRem() {
        assertThat(useCase(movementMagnitude = 0.08f, movementVariability = 0.05f))
            .isEqualTo(SleepStage.REM)
    }

    @Test
    fun invoke_remVariabilityBoundaries_areInclusive() {
        assertThat(useCase(movementMagnitude = 0.10f, movementVariability = 0.02f))
            .isEqualTo(SleepStage.REM)
        assertThat(useCase(movementMagnitude = 0.10f, movementVariability = 0.08f))
            .isEqualTo(SleepStage.REM)
    }

    @Test
    fun invoke_deepTakesPriorityOverRemWhenBothConditionsMatch() {
        // magnitude 0.04 / variability 0.03 satisfies both the DEEP and REM predicates —
        // DEEP is checked first in the `when`, so it must win.
        assertThat(useCase(movementMagnitude = 0.04f, movementVariability = 0.03f))
            .isEqualTo(SleepStage.DEEP)
    }

    @Test
    fun invoke_lowMovementHighVariability_fallsBackToLight() {
        // Below awake threshold, but variability too high for REM and magnitude too high for deep.
        assertThat(useCase(movementMagnitude = 0.05f, movementVariability = 0.12f))
            .isEqualTo(SleepStage.LIGHT)
    }

    @Test
    fun invoke_midRangeMagnitude_classifiesLight() {
        // Between REM_MAGNITUDE_MAX (0.10) and AWAKE_THRESHOLD (0.15) — neither deep nor REM nor awake.
        assertThat(useCase(movementMagnitude = 0.12f, movementVariability = 0.05f))
            .isEqualTo(SleepStage.LIGHT)
    }

    // ---- smoothStages() — 3-epoch median filter ----

    @Test
    fun smoothStages_fewerThanThreeEpochs_returnsUnchanged() {
        assertThat(useCase.smoothStages(emptyList())).isEmpty()
        assertThat(useCase.smoothStages(listOf(SleepStage.AWAKE))).containsExactly(SleepStage.AWAKE)
        assertThat(useCase.smoothStages(listOf(SleepStage.AWAKE, SleepStage.DEEP)))
            .containsExactly(SleepStage.AWAKE, SleepStage.DEEP)
            .inOrder()
    }

    @Test
    fun smoothStages_firstAndLastEpochs_areNeverReplaced() {
        val stages = listOf(SleepStage.AWAKE, SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP, SleepStage.REM)
        val smoothed = useCase.smoothStages(stages)
        assertThat(smoothed.first()).isEqualTo(SleepStage.AWAKE)
        assertThat(smoothed.last()).isEqualTo(SleepStage.REM)
    }

    @Test
    fun smoothStages_singleEpochNoiseSpike_isSmoothedToNeighboringMajority() {
        // A single AWAKE blip surrounded by DEEP on both sides should be smoothed away.
        val stages = listOf(SleepStage.DEEP, SleepStage.DEEP, SleepStage.AWAKE, SleepStage.DEEP, SleepStage.DEEP)
        val smoothed = useCase.smoothStages(stages)
        assertThat(smoothed).containsExactly(
            SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP, SleepStage.DEEP
        ).inOrder()
    }

    @Test
    fun smoothStages_clearMajorityInWindow_winsOverMinority() {
        // Window [LIGHT, LIGHT, DEEP] at index 2 — LIGHT is the majority (2 of 3).
        val stages = listOf(SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.REM)
        val smoothed = useCase.smoothStages(stages)
        assertThat(smoothed[2]).isEqualTo(SleepStage.LIGHT)
    }

    @Test
    fun smoothStages_preservesListLength() {
        val stages = listOf(
            SleepStage.AWAKE, SleepStage.LIGHT, SleepStage.DEEP,
            SleepStage.REM, SleepStage.LIGHT, SleepStage.AWAKE
        )
        assertThat(useCase.smoothStages(stages)).hasSize(stages.size)
    }
}
