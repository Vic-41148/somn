package dev.vic41148.somn.app.integration

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Standalone-channel contribution of the real updater into the [UpdateIntegration] set, alongside
 * the always-present no-op binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object StandaloneUpdateIntegrationModule {

    @Provides
    @IntoSet
    fun realUpdateIntegration(impl: StandaloneUpdateIntegration): UpdateIntegration = impl
}