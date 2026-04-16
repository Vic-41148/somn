package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.SleepDebtUseCase;
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
public final class AppModule_ProvideSleepDebtUseCaseFactory implements Factory<SleepDebtUseCase> {
  @Override
  public SleepDebtUseCase get() {
    return provideSleepDebtUseCase();
  }

  public static AppModule_ProvideSleepDebtUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SleepDebtUseCase provideSleepDebtUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSleepDebtUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideSleepDebtUseCaseFactory INSTANCE = new AppModule_ProvideSleepDebtUseCaseFactory();
  }
}
