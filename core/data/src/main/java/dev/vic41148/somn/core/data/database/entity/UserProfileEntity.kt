package dev.vic41148.somn.core.data.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for user biological profile.
 * Singleton table — always id=1.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Long = 1,
    val dateOfBirth: String? = null,        // ISO-8601: "2000-05-15"
    val biologicalSex: String = "NOT_SPECIFIED",
    val lifeStage: String = "DEFAULT",
    // Derived from chronotypeMeqScore at write time; reads recompute from the score in
    // UserProfileRepository (the raw score is the source of truth — this string may predate
    // the rMEQ band fix and be stale). Never write a deliberate value here that diverges from
    // the score; it will be silently overridden on read.
    val chronotype: String = "UNKNOWN",
    val chronotypeMeqScore: Int? = null,
    val adhdMode: Boolean = false,
    val asdMode: Boolean = false,
    val medicationTracking: Boolean = false,
    val targetSleepHours: Float = 8.0f,
    val pregnancyTrimester: Int? = null,
    val pregnancyDueDate: String? = null,   // ISO-8601
    val cycleLength: Int = 28,
    val lastPeriodStartDate: String? = null, // ISO-8601
    val timezoneId: String = "UTC",
    val onboardingCompleted: Boolean = false
)
