package dev.vic41148.somn.feature.habits;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.repository.HabitLogRepository;
import dev.vic41148.somn.core.data.repository.SleepRepository;
import dev.vic41148.somn.core.data.repository.UserProfileRepository;
import dev.vic41148.somn.core.domain.usecase.CorrelationUseCase;
import dev.vic41148.somn.core.domain.usecase.SleepDebtUseCase;
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
public final class HabitViewModel_Factory implements Factory<HabitViewModel> {
  private final Provider<HabitLogRepository> habitLogRepositoryProvider;

  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<UserProfileRepository> userProfileRepositoryProvider;

  private final Provider<SleepDebtUseCase> sleepDebtUseCaseProvider;

  private final Provider<CorrelationUseCase> correlationUseCaseProvider;

  private HabitViewModel_Factory(Provider<HabitLogRepository> habitLogRepositoryProvider,
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<UserProfileRepository> userProfileRepositoryProvider,
      Provider<SleepDebtUseCase> sleepDebtUseCaseProvider,
      Provider<CorrelationUseCase> correlationUseCaseProvider) {
    this.habitLogRepositoryProvider = habitLogRepositoryProvider;
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.userProfileRepositoryProvider = userProfileRepositoryProvider;
    this.sleepDebtUseCaseProvider = sleepDebtUseCaseProvider;
    this.correlationUseCaseProvider = correlationUseCaseProvider;
  }

  @Override
  public HabitViewModel get() {
    return newInstance(habitLogRepositoryProvider.get(), sleepRepositoryProvider.get(), userProfileRepositoryProvider.get(), sleepDebtUseCaseProvider.get(), correlationUseCaseProvider.get());
  }

  public static HabitViewModel_Factory create(
      Provider<HabitLogRepository> habitLogRepositoryProvider,
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<UserProfileRepository> userProfileRepositoryProvider,
      Provider<SleepDebtUseCase> sleepDebtUseCaseProvider,
      Provider<CorrelationUseCase> correlationUseCaseProvider) {
    return new HabitViewModel_Factory(habitLogRepositoryProvider, sleepRepositoryProvider, userProfileRepositoryProvider, sleepDebtUseCaseProvider, correlationUseCaseProvider);
  }

  public static HabitViewModel newInstance(HabitLogRepository habitLogRepository,
      SleepRepository sleepRepository, UserProfileRepository userProfileRepository,
      SleepDebtUseCase sleepDebtUseCase, CorrelationUseCase correlationUseCase) {
    return new HabitViewModel(habitLogRepository, sleepRepository, userProfileRepository, sleepDebtUseCase, correlationUseCase);
  }
}
