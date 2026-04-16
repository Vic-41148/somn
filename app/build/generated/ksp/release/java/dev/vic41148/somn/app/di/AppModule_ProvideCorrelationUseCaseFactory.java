package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.CorrelationUseCase;
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
public final class AppModule_ProvideCorrelationUseCaseFactory implements Factory<CorrelationUseCase> {
  @Override
  public CorrelationUseCase get() {
    return provideCorrelationUseCase();
  }

  public static AppModule_ProvideCorrelationUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CorrelationUseCase provideCorrelationUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideCorrelationUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideCorrelationUseCaseFactory INSTANCE = new AppModule_ProvideCorrelationUseCaseFactory();
  }
}
