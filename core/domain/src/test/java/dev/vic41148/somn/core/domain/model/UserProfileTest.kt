package dev.vic41148.somn.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * Regression tests for [UserProfile.recommendedSleepHours] — the canonical age→sleep-target
 * mapping that onboarding now delegates to.
 *
 * The OnboardingFlow SLEEP_GOAL screen used to duplicate only the 13-18/19-64/else subset of
 * these brackets inline, collapsing every age under 13 into the 65+ "7.5h" bucket: a 6-year-old
 * was told "we recommend 7.5 hours" while the stored target was actually 10h. The fix removed the
 * inline copy; these tests pin the bracket values themselves so the same class of mistake cannot
 * sneak back in anywhere that re-derives them.
 */
class UserProfileTest {

    private fun profileAged(years: Int) =
        UserProfile(dateOfBirth = LocalDate.now().minusYears(years.toLong()))

    @Test
    fun `no birth date defaults to 8 hours`() {
        assertThat(UserProfile().recommendedSleepHours).isEqualTo(8.0f)
    }

    @Test
    fun `age brackets match sleep-medicine guidance`() {
        assertThat(profileAged(0).recommendedSleepHours).isEqualTo(14.0f)
        assertThat(profileAged(2).recommendedSleepHours).isEqualTo(14.0f)
        assertThat(profileAged(3).recommendedSleepHours).isEqualTo(11.0f)
        assertThat(profileAged(5).recommendedSleepHours).isEqualTo(11.0f)
        assertThat(profileAged(6).recommendedSleepHours).isEqualTo(10.0f)
        assertThat(profileAged(12).recommendedSleepHours).isEqualTo(10.0f)
        assertThat(profileAged(13).recommendedSleepHours).isEqualTo(9.0f)
        assertThat(profileAged(18).recommendedSleepHours).isEqualTo(9.0f)
        assertThat(profileAged(19).recommendedSleepHours).isEqualTo(8.0f)
        assertThat(profileAged(64).recommendedSleepHours).isEqualTo(8.0f)
        assertThat(profileAged(65).recommendedSleepHours).isEqualTo(7.5f)
        assertThat(profileAged(80).recommendedSleepHours).isEqualTo(7.5f)
    }

    @Test
    fun `recommended hours never recommend less sleep for children than adults`() {
        // The exact failure mode of the removed inline copy: every age under 13 collapsed into
        // the 65+ bucket. No child may ever be recommended fewer hours than an adult.
        val childToTeen = (0..18).map(::profileAged)
        val adult = profileAged(40)
        assertThat(childToTeen.minOf { it.recommendedSleepHours })
            .isAtLeast(adult.recommendedSleepHours)
    }
}
