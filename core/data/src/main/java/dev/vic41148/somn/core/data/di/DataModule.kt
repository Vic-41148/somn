package dev.vic41148.somn.core.data.di

import android.content.Context
import androidx.room.Room
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.data.database.dao.AlarmDao
import dev.vic41148.somn.core.data.database.dao.HabitLogDao
import dev.vic41148.somn.core.data.database.dao.SleepEpochDao
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao
import dev.vic41148.somn.core.data.database.dao.TagDao
import dev.vic41148.somn.core.data.database.dao.UserProfileDao
import dev.vic41148.somn.core.data.database.dao.AudioEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SleepDatabase {
        return Room.databaseBuilder(
            context,
            SleepDatabase::class.java,
            SleepDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Dev-only: replace with proper migrations before release
            .build()
    }

    @Provides
    fun provideSleepSessionDao(db: SleepDatabase): SleepSessionDao = db.sleepSessionDao()

    @Provides
    fun provideSleepEpochDao(db: SleepDatabase): SleepEpochDao = db.sleepEpochDao()

    @Provides
    fun provideAlarmDao(db: SleepDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun provideTagDao(db: SleepDatabase): TagDao = db.tagDao()

    @Provides
    fun provideUserProfileDao(db: SleepDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideHabitLogDao(db: SleepDatabase): HabitLogDao = db.habitLogDao()

    @Provides
    fun provideAudioEventDao(db: SleepDatabase): AudioEventDao = db.audioEventDao()
}
