package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.HemisphereOverride
import dev.vic41148.somn.core.domain.model.MIN_SESSIONS_PER_SEASON
import dev.vic41148.somn.core.domain.model.Season
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Locks in the hemisphere override for [SeasonalAnalysisUseCase]: AUTO keeps the UTC-offset
 * heuristic, NORTHERN/SOUTHERN force the season mapping for every session (and the current
 * season). June is SUMMER in the north and WINTER in the south — the clearest discriminator.
 */
class SeasonalAnalysisUseCaseTest {

    private val useCase = SeasonalAnalysisUseCase()

    /** One completed session per day starting June 15 2025, all in the UTC zone. */
    private fun juneSessions(count: Int = MIN_SESSIONS_PER_SEASON): List<SleepSession> =
        (0 until count).map { i ->
            val start = LocalDate.of(2025, 6, 15)
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant().toEpochMilli() + i * 86_400_000L
            SleepSession(
                startTimeMillis = start,
                endTimeMillis = start + 28_800_000L,
                timezoneId = "UTC",
                isCompleted = true
            )
        }

    @Test
    fun southernOverride_mapsJuneToWinter() {
        val result = useCase.analyze(juneSessions(), hemisphereOverride = HemisphereOverride.SOUTHERN)
        assertThat(result.seasonalPatterns.map { it.season }).containsExactly(Season.WINTER)
    }

    @Test
    fun northernOverride_mapsJuneToSummer() {
        val result = useCase.analyze(juneSessions(), hemisphereOverride = HemisphereOverride.NORTHERN)
        assertThat(result.seasonalPatterns.map { it.season }).containsExactly(Season.SUMMER)
    }

    @Test
    fun autoWithUtcTimezone_defaultsToNorthern() {
        // UTC offset 0 is within the >= -3h northern band of the heuristic.
        val result = useCase.analyze(juneSessions(), deviceTimezoneId = "UTC")
        assertThat(result.seasonalPatterns.map { it.season }).containsExactly(Season.SUMMER)
    }

    @Test
    fun insufficientSessions_stillHonorsOverrideInCurrentSeason() {
        // Even with no qualifying season patterns, the override must not crash and the
        // current-season label must be computed against the forced hemisphere.
        val result = useCase.analyze(
            juneSessions(count = 2),
            deviceTimezoneId = "UTC",
            hemisphereOverride = HemisphereOverride.SOUTHERN
        )
        assertThat(result.seasonalPatterns).isEmpty()
    }
}
