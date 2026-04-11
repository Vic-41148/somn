package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.vic41148.somn.core.data.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for user biological profile.
 * Singleton pattern — only one row (id=1) ever exists.
 */
@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT onboardingCompleted FROM user_profile WHERE id = 1")
    suspend fun isOnboardingCompleted(): Boolean?

    @Query("SELECT onboardingCompleted FROM user_profile WHERE id = 1")
    fun observeOnboardingCompleted(): Flow<Boolean?>

    @Query("UPDATE user_profile SET onboardingCompleted = 1 WHERE id = 1")
    suspend fun markOnboardingCompleted()

    @Query("UPDATE user_profile SET lastPeriodStartDate = :date WHERE id = 1")
    suspend fun updateLastPeriodStart(date: String)

    @Query("UPDATE user_profile SET pregnancyTrimester = :trimester WHERE id = 1")
    suspend fun updatePregnancyTrimester(trimester: Int)
}
