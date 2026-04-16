package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.SocialJetLagUseCase;
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
public final class AppModule_ProvideSocialJetLagUseCaseFactory implements Factory<SocialJetLagUseCase> {
  @Override
  public SocialJetLagUseCase get() {
    return provideSocialJetLagUseCase();
  }

  public static AppModule_ProvideSocialJetLagUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SocialJetLagUseCase provideSocialJetLagUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSocialJetLagUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideSocialJetLagUseCaseFactory INSTANCE = new AppModule_ProvideSocialJetLagUseCaseFactory();
  }
}
