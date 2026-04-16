package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.ChronotypeAssessmentUseCase;
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
public final class AppModule_ProvideChronotypeAssessmentUseCaseFactory implements Factory<ChronotypeAssessmentUseCase> {
  @Override
  public ChronotypeAssessmentUseCase get() {
    return provideChronotypeAssessmentUseCase();
  }

  public static AppModule_ProvideChronotypeAssessmentUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ChronotypeAssessmentUseCase provideChronotypeAssessmentUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideChronotypeAssessmentUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideChronotypeAssessmentUseCaseFactory INSTANCE = new AppModule_ProvideChronotypeAssessmentUseCaseFactory();
  }
}
