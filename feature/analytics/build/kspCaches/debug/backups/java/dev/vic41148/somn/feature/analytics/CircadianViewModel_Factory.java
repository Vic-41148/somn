package dev.vic41148.somn.feature.analytics;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.repository.SleepRepository;
import dev.vic41148.somn.core.data.repository.UserProfileRepository;
import dev.vic41148.somn.core.domain.usecase.ChronotypeAssessmentUseCase;
import dev.vic41148.somn.core.domain.usecase.SeasonalAnalysisUseCase;
import dev.vic41148.somn.core.domain.usecase.SocialJetLagUseCase;
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
public final class CircadianViewModel_Factory implements Factory<CircadianViewModel> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<UserProfileRepository> userProfileRepositoryProvider;

  private final Provider<ChronotypeAssessmentUseCase> chronotypeAssessmentUseCaseProvider;

  private final Provider<SocialJetLagUseCase> socialJetLagUseCaseProvider;

  private final Provider<SeasonalAnalysisUseCase> seasonalAnalysisUseCaseProvider;

  private CircadianViewModel_Factory(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<UserProfileRepository> userProfileRepositoryProvider,
      Provider<ChronotypeAssessmentUseCase> chronotypeAssessmentUseCaseProvider,
      Provider<SocialJetLagUseCase> socialJetLagUseCaseProvider,
      Provider<SeasonalAnalysisUseCase> seasonalAnalysisUseCaseProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.userProfileRepositoryProvider = userProfileRepositoryProvider;
    this.chronotypeAssessmentUseCaseProvider = chronotypeAssessmentUseCaseProvider;
    this.socialJetLagUseCaseProvider = socialJetLagUseCaseProvider;
    this.seasonalAnalysisUseCaseProvider = seasonalAnalysisUseCaseProvider;
  }

  @Override
  public CircadianViewModel get() {
    return newInstance(sleepRepositoryProvider.get(), userProfileRepositoryProvider.get(), chronotypeAssessmentUseCaseProvider.get(), socialJetLagUseCaseProvider.get(), seasonalAnalysisUseCaseProvider.get());
  }

  public static CircadianViewModel_Factory create(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<UserProfileRepository> userProfileRepositoryProvider,
      Provider<ChronotypeAssessmentUseCase> chronotypeAssessmentUseCaseProvider,
      Provider<SocialJetLagUseCase> socialJetLagUseCaseProvider,
      Provider<SeasonalAnalysisUseCase> seasonalAnalysisUseCaseProvider) {
    return new CircadianViewModel_Factory(sleepRepositoryProvider, userProfileRepositoryProvider, chronotypeAssessmentUseCaseProvider, socialJetLagUseCaseProvider, seasonalAnalysisUseCaseProvider);
  }

  public static CircadianViewModel newInstance(SleepRepository sleepRepository,
      UserProfileRepository userProfileRepository,
      ChronotypeAssessmentUseCase chronotypeAssessmentUseCase,
      SocialJetLagUseCase socialJetLagUseCase, SeasonalAnalysisUseCase seasonalAnalysisUseCase) {
    return new CircadianViewModel(sleepRepository, userProfileRepository, chronotypeAssessmentUseCase, socialJetLagUseCase, seasonalAnalysisUseCase);
  }
}
