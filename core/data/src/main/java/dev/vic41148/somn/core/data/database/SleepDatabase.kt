package dev.vic41148.somn.core.data.database

import androidx.room.Database
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
import dev.vic41148.somn.core.data.database.entity.ExternalVitalsEntity
import dev.vic41148.somn.core.data.database.dao.ExternalVitalsDao

@Database(
    entities = [
        SleepSessionEntity::class,
        SleepEpochEntity::class,
        AlarmEntity::class,
        TagEntity::class,
        SessionTagEntity::class,
        UserProfileEntity::class,
        HabitLogEntity::class,
        AudioEventEntity::class,
        ExternalVitalsEntity::class
    ],
    version = 13,  // HEALTH-01/02/04: external_vitals table + healthConnectRecordId on sleep_sessions
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
    abstract fun externalVitalsDao(): ExternalVitalsDao

    companion object {
        const val DATABASE_NAME = "sleep_tracker.db"
    }
}
