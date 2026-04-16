package dev.vic41148.somn.core.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.vic41148.somn.core.data.database.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {

    /** All entries for a specific date. */
    @Query("SELECT * FROM habit_logs WHERE date = :date ORDER BY id DESC")
    fun getLogsForDate(date: String): Flow<List<HabitLogEntity>>

    /** All entries in an inclusive date range, newest first. */
    @Query("SELECT * FROM habit_logs WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun getLogsInRange(from: String, to: String): Flow<List<HabitLogEntity>>

    /** All entries of a specific type across all time (for correlation analysis). */
    @Query("SELECT * FROM habit_logs WHERE entryType = :type ORDER BY date DESC")
    fun getLogsByType(type: String): Flow<List<HabitLogEntity>>

    /** Most recent N logs of a given type (for medication history). */
    @Query("SELECT * FROM habit_logs WHERE entryType = :type ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentLogsByType(type: String, limit: Int): Flow<List<HabitLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HabitLogEntity): Long

    @Delete
    suspend fun delete(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
