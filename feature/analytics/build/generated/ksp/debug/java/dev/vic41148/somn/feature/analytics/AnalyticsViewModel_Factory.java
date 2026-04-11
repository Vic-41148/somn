package dev.vic41148.somn.feature.analytics;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.repository.SleepRepository;
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase;
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
public final class AnalyticsViewModel_Factory implements Factory<AnalyticsViewModel> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<ExportCsvUseCase> exportCsvProvider;

  private AnalyticsViewModel_Factory(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<ExportCsvUseCase> exportCsvProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.exportCsvProvider = exportCsvProvider;
  }

  @Override
  public AnalyticsViewModel get() {
    return newInstance(sleepRepositoryProvider.get(), exportCsvProvider.get());
  }

  public static AnalyticsViewModel_Factory create(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<ExportCsvUseCase> exportCsvProvider) {
    return new AnalyticsViewModel_Factory(sleepRepositoryProvider, exportCsvProvider);
  }

  public static AnalyticsViewModel newInstance(SleepRepository sleepRepository,
      ExportCsvUseCase exportCsv) {
    return new AnalyticsViewModel(sleepRepository, exportCsv);
  }
}
