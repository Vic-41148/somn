package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideExportCsvUseCaseFactory implements Factory<ExportCsvUseCase> {
  @Override
  public ExportCsvUseCase get() {
    return provideExportCsvUseCase();
  }

  public static AppModule_ProvideExportCsvUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ExportCsvUseCase provideExportCsvUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideExportCsvUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideExportCsvUseCaseFactory INSTANCE = new AppModule_ProvideExportCsvUseCaseFactory();
  }
}
