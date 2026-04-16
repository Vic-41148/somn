package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.SeasonalAnalysisUseCase;
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
public final class AppModule_ProvideSeasonalAnalysisUseCaseFactory implements Factory<SeasonalAnalysisUseCase> {
  @Override
  public SeasonalAnalysisUseCase get() {
    return provideSeasonalAnalysisUseCase();
  }

  public static AppModule_ProvideSeasonalAnalysisUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SeasonalAnalysisUseCase provideSeasonalAnalysisUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSeasonalAnalysisUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideSeasonalAnalysisUseCaseFactory INSTANCE = new AppModule_ProvideSeasonalAnalysisUseCaseFactory();
  }
}
