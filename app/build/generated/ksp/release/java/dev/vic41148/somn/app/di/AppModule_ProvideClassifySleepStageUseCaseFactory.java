package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.ClassifySleepStageUseCase;
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
public final class AppModule_ProvideClassifySleepStageUseCaseFactory implements Factory<ClassifySleepStageUseCase> {
  @Override
  public ClassifySleepStageUseCase get() {
    return provideClassifySleepStageUseCase();
  }

  public static AppModule_ProvideClassifySleepStageUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ClassifySleepStageUseCase provideClassifySleepStageUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideClassifySleepStageUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideClassifySleepStageUseCaseFactory INSTANCE = new AppModule_ProvideClassifySleepStageUseCaseFactory();
  }
}
