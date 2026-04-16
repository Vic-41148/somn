package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.CalculateSleepScoreUseCase;
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
public final class AppModule_ProvideCalculateSleepScoreUseCaseFactory implements Factory<CalculateSleepScoreUseCase> {
  @Override
  public CalculateSleepScoreUseCase get() {
    return provideCalculateSleepScoreUseCase();
  }

  public static AppModule_ProvideCalculateSleepScoreUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CalculateSleepScoreUseCase provideCalculateSleepScoreUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideCalculateSleepScoreUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideCalculateSleepScoreUseCaseFactory INSTANCE = new AppModule_ProvideCalculateSleepScoreUseCaseFactory();
  }
}
