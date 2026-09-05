package dev.vic41148.somn.core.data.di

import android.content.Context
import androidx.room.Room
import dev.vic41148.somn.core.data.backup.EncryptionUtils
import dev.vic41148.somn.core.data.backup.NasClient
import dev.vic41148.somn.core.data.backup.NasClientImpl
import dev.vic41148.somn.core.data.database.ALL_MIGRATIONS
import dev.vic41148.somn.core.data.database.DatabaseKeyManager
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.data.database.dao.AlarmDao
import dev.vic41148.somn.core.data.database.dao.HabitLogDao
import dev.vic41148.somn.core.data.database.dao.SleepEpochDao
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao
import dev.vic41148.somn.core.data.database.dao.TagDao
import dev.vic41148.somn.core.data.database.dao.UserProfileDao
import dev.vic41148.somn.core.data.database.dao.AudioEventDao
import dev.vic41148.somn.core.data.database.dao.ExternalVitalsDao
import dev.vic41148.somn.core.data.database.dao.AlarmEventDao
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
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): SleepDatabase {
        return Room.databaseBuilder(
            context,
            SleepDatabase::class.java,
            SleepDatabase.DATABASE_NAME
        )
            .openHelperFactory(
                // At-rest encryption: SQLCipher with a Keystore-wrapped random key. First
                // launch on a v0.1.2 install migrates the plaintext DB in place.
                net.sqlcipher.database.SupportFactory(keyManager.getOrCreatePassphrase())
            )
            .addMigrations(*ALL_MIGRATIONS)
            // v1 predates exportSchema and never shipped. Every later version migrates properly.
            .fallbackToDestructiveMigrationFrom(1)
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

    @Provides
    fun provideExternalVitalsDao(db: SleepDatabase): ExternalVitalsDao = db.externalVitalsDao()

    @Provides
    fun provideAlarmEventDao(db: SleepDatabase): AlarmEventDao = db.alarmEventDao()

    @Provides
    @Singleton
    fun provideNasClient(
        preferencesRepository: dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
    ): NasClient = NasClientImpl(preferencesRepository)

    @Provides
    @Singleton
    fun provideEncryptionUtils(): EncryptionUtils = EncryptionUtils()
}
