package dev.vic41148.somn.app.integration

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Base contribution into the [UpdateIntegration] set. Every channel (including store) compiles
 * this no-op so the shared UI compiles against a non-empty set. The standalone channel's own
 * module adds the real implementation alongside it.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateIntegrationModule {

    @Provides
    @IntoSet
    fun noopUpdateIntegration(): UpdateIntegration = object : UpdateIntegration {}
}