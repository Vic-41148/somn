package dev.vic41148.somn.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks in the rMEQ (4-25) band mapping for [Chronotype.fromMeqScore].
 *
 * Regression test for the scale-mismatch bug where the classifier used the 19-item Horne &
 * Östberg MEQ bands (16-86) on scores produced by the 5-item rMEQ quiz (4-25): every morning
 * band was structurally unreachable, genuine morning types were labeled evening, and most
 * evening types landed in UNKNOWN.
 */
class ChronotypeTest {

    @Test
    fun fromMeqScore_eveningBands_mapCorrectly() {
        assertThat(Chronotype.fromMeqScore(4)).isEqualTo(Chronotype.DEFINITE_EVENING)
        assertThat(Chronotype.fromMeqScore(8)).isEqualTo(Chronotype.DEFINITE_EVENING)
        assertThat(Chronotype.fromMeqScore(9)).isEqualTo(Chronotype.MODERATE_EVENING)
        assertThat(Chronotype.fromMeqScore(12)).isEqualTo(Chronotype.MODERATE_EVENING)
    }

    @Test
    fun fromMeqScore_intermediateBand_mapsCorrectly() {
        assertThat(Chronotype.fromMeqScore(13)).isEqualTo(Chronotype.INTERMEDIATE)
        assertThat(Chronotype.fromMeqScore(16)).isEqualTo(Chronotype.INTERMEDIATE)
    }

    @Test
    fun fromMeqScore_morningBands_mapCorrectly() {
        assertThat(Chronotype.fromMeqScore(17)).isEqualTo(Chronotype.MODERATE_MORNING)
        assertThat(Chronotype.fromMeqScore(20)).isEqualTo(Chronotype.MODERATE_MORNING)
        assertThat(Chronotype.fromMeqScore(21)).isEqualTo(Chronotype.DEFINITE_MORNING)
        assertThat(Chronotype.fromMeqScore(25)).isEqualTo(Chronotype.DEFINITE_MORNING)
    }

    @Test
    fun fromMeqScore_quizExtremes_areDefiniteEveningAndMorning() {
        // Actual achievable range of the 5-item quiz: 4 (all evening answers) .. 25 (all morning)
        assertThat(Chronotype.fromMeqScore(4)).isEqualTo(Chronotype.DEFINITE_EVENING)
        assertThat(Chronotype.fromMeqScore(25)).isEqualTo(Chronotype.DEFINITE_MORNING)
    }

    @Test
    fun fromMeqScore_outOfRange_returnsUnknown() {
        assertThat(Chronotype.fromMeqScore(3)).isEqualTo(Chronotype.UNKNOWN)
        assertThat(Chronotype.fromMeqScore(26)).isEqualTo(Chronotype.UNKNOWN)
        // Old-scale values must no longer classify as anything
        assertThat(Chronotype.fromMeqScore(42)).isEqualTo(Chronotype.UNKNOWN)
        assertThat(Chronotype.fromMeqScore(59)).isEqualTo(Chronotype.UNKNOWN)
        assertThat(Chronotype.fromMeqScore(86)).isEqualTo(Chronotype.UNKNOWN)
        assertThat(Chronotype.fromMeqScore(-1)).isEqualTo(Chronotype.UNKNOWN)
    }
}
