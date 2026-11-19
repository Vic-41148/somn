package dev.vic41148.somn.feature.settings.ui

import dev.vic41148.somn.core.domain.model.UserProfile

/**
 * True when the profile has any cycle-relevant feature: menstrual cycle tracking, pregnancy,
 * or peri/menopause. Used to keep the "Cycle & hormones" card out of Settings for male and
 * NOT_SPECIFIED profiles (#116).
 */
internal fun UserProfile?.hasCycleProfile(): Boolean =
    this?.showCycleFeatures == true ||
        this?.showMenopauseFeatures == true ||
        this?.showPregnancyFeatures == true
