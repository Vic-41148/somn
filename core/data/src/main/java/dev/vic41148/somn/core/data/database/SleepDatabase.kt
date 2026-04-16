package dev.vic41148.somn.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.vic41148.somn.core.data.database.dao.AlarmDao
import dev.vic41148.somn.core.data.database.dao.HabitLogDao
import dev.vic41148.somn.core.data.database.dao.SleepEpochDao
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao
import dev.vic41148.somn.core.data.database.dao.TagDao
import dev.vic41148.somn.core.data.database.dao.UserProfileDao
import dev.vic41148.somn.core.data.database.entity.AlarmEntity
import dev.vic41148.somn.core.data.database.entity.HabitLogEntity
import dev.vic41148.somn.core.data.database.entity.SessionTagEntity
import dev.vic41148.somn.core.data.database.entity.SleepEpochEntity
import dev.vic41148.somn.core.data.database.entity.SleepSessionEntity
import dev.vic41148.somn.core.data.database.entity.TagEntity
import dev.vic41148.somn.core.data.database.entity.UserProfileEntity
import dev.vic41148.somn.core.data.database.entity.AudioEventEntity
import dev.vic41148.somn.core.data.database.dao.AudioEventDao

@Database(
    entities = [
        SleepSessionEntity::class,
        SleepEpochEntity::class,
        AlarmEntity::class,
        TagEntity::class,
        SessionTagEntity::class,
        UserProfileEntity::class,
        HabitLogEntity::class,
        AudioEventEntity::class
    ],
    version = 8,  // Phase 6: added clipPath to AudioEventEntity and coughEventCount to SleepSessionEntity
    exportSchema = true
)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepEpochDao(): SleepEpochDao
    abstract fun alarmDao(): AlarmDao
    abstract fun tagDao(): TagDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun audioEventDao(): AudioEventDao

    companion object {
        const val DATABASE_NAME = "sleep_tracker.db"

        @Volatile
        private var INSTANCE: SleepDatabase? = null

        /**
         * Thread-safe singleton accessor for contexts where Hilt is unavailable
         * (e.g. AppWidgetProvider). Uses the same database file as DataModule.
         */
        fun getInstance(context: Context): SleepDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SleepDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
