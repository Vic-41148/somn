package dev.vic41148.somn.app.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.domain.usecase.SmartAlarmUseCase;
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
public final class AppModule_ProvideSmartAlarmUseCaseFactory implements Factory<SmartAlarmUseCase> {
  @Override
  public SmartAlarmUseCase get() {
    return provideSmartAlarmUseCase();
  }

  public static AppModule_ProvideSmartAlarmUseCaseFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SmartAlarmUseCase provideSmartAlarmUseCase() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSmartAlarmUseCase());
  }

  private static final class InstanceHolder {
    static final AppModule_ProvideSmartAlarmUseCaseFactory INSTANCE = new AppModule_ProvideSmartAlarmUseCaseFactory();
  }
}
