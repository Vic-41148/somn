package dev.vic41148.somn.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.vic41148.somn.core.data.haptic.AndroidHapticsManager
import dev.vic41148.somn.core.domain.haptic.HapticsManager
import javax.inject.Singleton

/**
 * Binds the [AndroidHapticsManager] concrete class behind the [HapticsManager] interface so
 * ViewModels, workers, activities, and Compose screens all inject the abstraction and never a
 * `Vibrator` or `HapticFeedbackType` directly.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HapticsModule {

    @Binds
    @Singleton
    abstract fun bindHapticsManager(impl: AndroidHapticsManager): HapticsManager
}