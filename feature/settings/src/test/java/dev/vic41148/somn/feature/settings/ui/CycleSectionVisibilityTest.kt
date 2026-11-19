package dev.vic41148.somn.feature.settings.ui

import dev.vic41148.somn.core.domain.model.BiologicalSex
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.UserProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for #116: the "Cycle & hormones" card must not render for male or
 * NOT_SPECIFIED profiles, where it used to appear as an empty card.
 */
class CycleSectionVisibilityTest {

    @Test
    fun `female default profile has cycle features`() {
        assertTrue(profile(BiologicalSex.FEMALE, LifeStage.DEFAULT).hasCycleProfile())
    }

    @Test
    fun `female cycling profile has cycle features`() {
        assertTrue(profile(BiologicalSex.FEMALE, LifeStage.CYCLING).hasCycleProfile())
    }

    @Test
    fun `pregnant profile has cycle features`() {
        assertTrue(profile(BiologicalSex.FEMALE, LifeStage.PREGNANT).hasCycleProfile())
    }

    @Test
    fun `menopause profiles have cycle features`() {
        assertTrue(profile(BiologicalSex.FEMALE, LifeStage.PERIMENOPAUSE).hasCycleProfile())
        assertTrue(profile(BiologicalSex.FEMALE, LifeStage.MENOPAUSE).hasCycleProfile())
        assertTrue(profile(BiologicalSex.FEMALE, LifeStage.POST_MENOPAUSE).hasCycleProfile())
    }

    @Test
    fun `male profile has no cycle features`() {
        assertFalse(profile(BiologicalSex.MALE, LifeStage.DEFAULT).hasCycleProfile())
    }

    @Test
    fun `not specified profile has no cycle features`() {
        assertFalse(profile(BiologicalSex.NOT_SPECIFIED, LifeStage.DEFAULT).hasCycleProfile())
    }

    @Test
    fun `null profile has no cycle features`() {
        assertFalse((null as UserProfile?).hasCycleProfile())
    }

    private fun profile(biologicalSex: BiologicalSex, lifeStage: LifeStage) =
        UserProfile(biologicalSex = biologicalSex, lifeStage = lifeStage)
}
