package dev.vic41148.somn.core.data.retention

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * PRIVACY.md tells users their sleep-talk recordings are deleted after seven days by default.
 * These pin that claim to something executable.
 */
class ClipRetentionPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `default retention puts the cutoff seven days back`() {
        val cutoff = ClipRetentionPolicy.cutoffMillis(
            now,
            SomnPreferencesRepository.DEFAULT_CLIP_RETENTION_DAYS
        )

        assertThat(cutoff).isEqualTo(now - TimeUnit.DAYS.toMillis(7))
    }

    @Test
    fun `the documented default is seven days`() {
        // The number itself is a user-facing promise, not an implementation detail.
        assertThat(SomnPreferencesRepository.DEFAULT_CLIP_RETENTION_DAYS).isEqualTo(7)
    }

    @Test
    fun `the keep-forever sentinel disables pruning`() {
        val cutoff = ClipRetentionPolicy.cutoffMillis(
            now,
            SomnPreferencesRepository.CLIP_RETENTION_KEEP_FOREVER
        )

        assertThat(cutoff).isNull()
    }

    @Test
    fun `a corrupted negative preference keeps clips rather than deleting everything`() {
        // A negative day count could only come from a corrupted preference. Treating it as a
        // cutoff in the future would delete every recording the user has — fail safe instead.
        assertThat(ClipRetentionPolicy.cutoffMillis(now, -30)).isNull()
    }

    @Test
    fun `a one day window only expires clips older than a day`() {
        val cutoff = ClipRetentionPolicy.cutoffMillis(now, 1)!!

        val twentyThreeHoursOld = now - TimeUnit.HOURS.toMillis(23)
        val twentyFiveHoursOld = now - TimeUnit.HOURS.toMillis(25)

        assertThat(twentyThreeHoursOld).isGreaterThan(cutoff)
        assertThat(twentyFiveHoursOld).isLessThan(cutoff)
    }
}
