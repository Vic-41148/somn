package dev.vic41148.somn.feature.settings;

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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<ExportCsvUseCase> exportCsvProvider;

  private SettingsViewModel_Factory(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<ExportCsvUseCase> exportCsvProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.exportCsvProvider = exportCsvProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(sleepRepositoryProvider.get(), exportCsvProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<ExportCsvUseCase> exportCsvProvider) {
    return new SettingsViewModel_Factory(sleepRepositoryProvider, exportCsvProvider);
  }

  public static SettingsViewModel newInstance(SleepRepository sleepRepository,
      ExportCsvUseCase exportCsv) {
    return new SettingsViewModel(sleepRepository, exportCsv);
  }
}
