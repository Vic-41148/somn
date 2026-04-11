package dev.vic41148.somn.app.di

import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCalculateSleepScoreUseCase(): CalculateSleepScoreUseCase {
        return CalculateSleepScoreUseCase()
    }

    @Provides
    @Singleton
    fun provideClassifySleepStageUseCase(): ClassifySleepStageUseCase {
        return ClassifySleepStageUseCase()
    }

    @Provides
    @Singleton
    fun provideExportCsvUseCase(): ExportCsvUseCase {
        return ExportCsvUseCase()
    }
}
