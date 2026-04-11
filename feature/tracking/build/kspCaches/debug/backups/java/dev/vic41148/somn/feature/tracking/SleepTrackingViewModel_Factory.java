package dev.vic41148.somn.feature.tracking;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.repository.SleepRepository;
import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase;
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class SleepTrackingViewModel_Factory implements Factory<SleepTrackingViewModel> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<CalculateSleepScoreUseCase> calculateScoreProvider;

  private final Provider<ClassifySleepStageUseCase> classifyStageProvider;

  private SleepTrackingViewModel_Factory(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<CalculateSleepScoreUseCase> calculateScoreProvider,
      Provider<ClassifySleepStageUseCase> classifyStageProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.calculateScoreProvider = calculateScoreProvider;
    this.classifyStageProvider = classifyStageProvider;
  }

  @Override
  public SleepTrackingViewModel get() {
    return newInstance(sleepRepositoryProvider.get(), calculateScoreProvider.get(), classifyStageProvider.get());
  }

  public static SleepTrackingViewModel_Factory create(
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<CalculateSleepScoreUseCase> calculateScoreProvider,
      Provider<ClassifySleepStageUseCase> classifyStageProvider) {
    return new SleepTrackingViewModel_Factory(sleepRepositoryProvider, calculateScoreProvider, classifyStageProvider);
  }

  public static SleepTrackingViewModel newInstance(SleepRepository sleepRepository,
      CalculateSleepScoreUseCase calculateScore, ClassifySleepStageUseCase classifyStage) {
    return new SleepTrackingViewModel(sleepRepository, calculateScore, classifyStage);
  }
}
