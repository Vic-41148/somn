package dev.vic41148.somn.app.di

import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase
import dev.vic41148.somn.core.domain.usecase.CorrelationUseCase
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dev.vic41148.somn.core.domain.usecase.SleepDebtUseCase
import dev.vic41148.somn.core.domain.usecase.ChronotypeAssessmentUseCase
import dev.vic41148.somn.core.domain.usecase.SocialJetLagUseCase
import dev.vic41148.somn.core.domain.usecase.SeasonalAnalysisUseCase
import dev.vic41148.somn.core.domain.usecase.SmartAlarmUseCase
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

    @Provides
    @Singleton
    fun provideSleepDebtUseCase(): SleepDebtUseCase {
        return SleepDebtUseCase()
    }

    @Provides
    @Singleton
    fun provideCorrelationUseCase(): CorrelationUseCase {
        return CorrelationUseCase()
    }

    @Provides
    @Singleton
    fun provideChronotypeAssessmentUseCase(): ChronotypeAssessmentUseCase {
        return ChronotypeAssessmentUseCase()
    }

    @Provides
    @Singleton
    fun provideSocialJetLagUseCase(): SocialJetLagUseCase {
        return SocialJetLagUseCase()
    }

    @Provides
    @Singleton
    fun provideSeasonalAnalysisUseCase(): SeasonalAnalysisUseCase {
        return SeasonalAnalysisUseCase()
    }

    @Provides
    @Singleton
    fun provideSmartAlarmUseCase(): SmartAlarmUseCase {
        return SmartAlarmUseCase()
    }
}
