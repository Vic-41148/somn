package dev.vic41148.somn.feature.alarm.di

import dev.vic41148.somn.core.domain.repository.AlarmScheduler
import dev.vic41148.somn.feature.alarm.service.AlarmSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmSchedulerModule {
    @Binds
    abstract fun bindAlarmScheduler(impl: AlarmSchedulerImpl): AlarmScheduler
}
