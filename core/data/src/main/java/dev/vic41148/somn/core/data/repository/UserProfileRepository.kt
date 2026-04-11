package dev.vic41148.somn.core.data.repository

import dev.vic41148.somn.core.data.database.dao.UserProfileDao
import dev.vic41148.somn.core.data.database.entity.UserProfileEntity
import dev.vic41148.somn.core.domain.model.BiologicalSex
import dev.vic41148.somn.core.domain.model.Chronotype
import dev.vic41148.somn.core.domain.model.LifeStage
import dev.vic41148.somn.core.domain.model.MenstrualCyclePhase
import dev.vic41148.somn.core.domain.model.NeurodivergentProfile
import dev.vic41148.somn.core.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val profileDao: UserProfileDao
) {

    // --- Observe ---

    fun observeProfile(): Flow<UserProfile?> {
        return profileDao.observeProfile().map { it?.toDomain() }
    }

    fun observeOnboardingCompleted(): Flow<Boolean> {
        return profileDao.observeOnboardingCompleted().map { it ?: false }
    }

    // --- Read ---

    suspend fun getProfile(): UserProfile? {
        return profileDao.getProfile()?.toDomain()
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return profileDao.isOnboardingCompleted() ?: false
    }

    // --- Write ---

    suspend fun saveProfile(profile: UserProfile) {
        profileDao.upsert(profile.toEntity())
    }

    suspend fun markOnboardingCompleted() {
        profileDao.markOnboardingCompleted()
    }

    suspend fun updateLastPeriodStart(date: LocalDate) {
        profileDao.updateLastPeriodStart(date.toString())
    }

    suspend fun updatePregnancyTrimester(trimester: Int) {
        profileDao.updatePregnancyTrimester(trimester)
    }

    // --- Convenience ---

    /**
     * Get the current menstrual cycle phase based on stored profile data.
     * Returns null if cycle tracking is not active or data is insufficient.
     */
    suspend fun getCurrentCyclePhase(): MenstrualCyclePhase? {
        val profile = getProfile() ?: return null
        if (!profile.showCycleFeatures) return null
        return MenstrualCyclePhase.currentPhase(
            lastPeriodStart = profile.lastPeriodStartDate,
            cycleLength = profile.cycleLength
        )
    }

    /**
     * Get the current cycle day (1-based), or null.
     */
    suspend fun getCurrentCycleDay(): Int? {
        val profile = getProfile() ?: return null
        if (!profile.showCycleFeatures) return null
        return MenstrualCyclePhase.cycleDay(
            lastPeriodStart = profile.lastPeriodStartDate,
            cycleLength = profile.cycleLength
        )
    }

    // --- Mappers ---

    private fun UserProfileEntity.toDomain() = UserProfile(
        id = id,
        dateOfBirth = dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        biologicalSex = runCatching { BiologicalSex.valueOf(biologicalSex) }
            .getOrDefault(BiologicalSex.NOT_SPECIFIED),
        lifeStage = runCatching { LifeStage.valueOf(lifeStage) }
            .getOrDefault(LifeStage.DEFAULT),
        chronotype = runCatching { Chronotype.valueOf(chronotype) }
            .getOrDefault(Chronotype.UNKNOWN),
        chronotypeMeqScore = chronotypeMeqScore,
        neurodivergentProfile = NeurodivergentProfile(
            adhdMode = adhdMode,
            asdMode = asdMode,
            medicationTracking = medicationTracking
        ),
        targetSleepHours = targetSleepHours,
        pregnancyTrimester = pregnancyTrimester,
        pregnancyDueDate = pregnancyDueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        cycleLength = cycleLength,
        lastPeriodStartDate = lastPeriodStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        shiftWorker = shiftWorker,
        timezoneId = timezoneId,
        onboardingCompleted = onboardingCompleted
    )

    private fun UserProfile.toEntity() = UserProfileEntity(
        id = id,
        dateOfBirth = dateOfBirth?.toString(),
        biologicalSex = biologicalSex.name,
        lifeStage = lifeStage.name,
        chronotype = chronotype.name,
        chronotypeMeqScore = chronotypeMeqScore,
        adhdMode = neurodivergentProfile.adhdMode,
        asdMode = neurodivergentProfile.asdMode,
        medicationTracking = neurodivergentProfile.medicationTracking,
        targetSleepHours = targetSleepHours,
        pregnancyTrimester = pregnancyTrimester,
        pregnancyDueDate = pregnancyDueDate?.toString(),
        cycleLength = cycleLength,
        lastPeriodStartDate = lastPeriodStartDate?.toString(),
        shiftWorker = shiftWorker,
        timezoneId = timezoneId,
        onboardingCompleted = onboardingCompleted
    )
}
