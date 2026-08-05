package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks in the sample-size thresholds behind [CorrelationConfidence.from] — the qualifier that
 * tells users a correlation computed from a bare-minimum week of data is still a tentative read
 * rather than a settled finding (small-n Pearson coefficients carry very wide confidence
 * intervals).
 */
class CorrelationConfidenceTest {

    @Test
    fun from_belowTwoWeeks_isLow() {
        assertThat(CorrelationConfidence.from(7)).isEqualTo(CorrelationConfidence.LOW)
        assertThat(CorrelationConfidence.from(10)).isEqualTo(CorrelationConfidence.LOW)
        assertThat(CorrelationConfidence.from(13)).isEqualTo(CorrelationConfidence.LOW)
    }

    @Test
    fun from_twoToFourWeeks_isMedium() {
        assertThat(CorrelationConfidence.from(14)).isEqualTo(CorrelationConfidence.MEDIUM)
        assertThat(CorrelationConfidence.from(21)).isEqualTo(CorrelationConfidence.MEDIUM)
        assertThat(CorrelationConfidence.from(29)).isEqualTo(CorrelationConfidence.MEDIUM)
    }

    @Test
    fun from_monthOrMore_isHigh() {
        assertThat(CorrelationConfidence.from(30)).isEqualTo(CorrelationConfidence.HIGH)
        assertThat(CorrelationConfidence.from(60)).isEqualTo(CorrelationConfidence.HIGH)
        assertThat(CorrelationConfidence.from(365)).isEqualTo(CorrelationConfidence.HIGH)
    }
}
