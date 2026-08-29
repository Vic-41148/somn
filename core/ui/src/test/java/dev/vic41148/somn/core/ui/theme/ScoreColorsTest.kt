package dev.vic41148.somn.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreColorsTest {

    @Test
    fun `tier boundaries land on the documented thresholds`() {
        assertEquals(ScoreTier.GREAT, ScoreTier.of(100))
        assertEquals(ScoreTier.GREAT, ScoreTier.of(80))
        assertEquals(ScoreTier.GOOD, ScoreTier.of(79))
        assertEquals(ScoreTier.GOOD, ScoreTier.of(60))
        assertEquals(ScoreTier.FAIR, ScoreTier.of(59))
        assertEquals(ScoreTier.FAIR, ScoreTier.of(40))
        assertEquals(ScoreTier.POOR, ScoreTier.of(39))
        assertEquals(ScoreTier.POOR, ScoreTier.of(0))
    }

    @Test
    fun `scoreColor uses the shared ramp`() {
        assertEquals(ScoreGreat, scoreColor(80))
        assertEquals(ScoreGood, scoreColor(60))
        assertEquals(ScoreFair, scoreColor(40))
        assertEquals(ScorePoor, scoreColor(0))
    }

    @Test
    fun `labels match the tier wording shown to users`() {
        assertEquals("Great", ScoreTier.of(85).label)
        assertEquals("Good", ScoreTier.of(60).label)
        assertEquals("Fair", ScoreTier.of(40).label)
        assertEquals("Poor", ScoreTier.of(0).label)
    }
}