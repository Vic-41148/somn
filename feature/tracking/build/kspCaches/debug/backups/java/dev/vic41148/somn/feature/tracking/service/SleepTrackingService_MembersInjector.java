package dev.vic41148.somn.feature.tracking.service;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dev.vic41148.somn.core.data.repository.AlarmRepository;
import dev.vic41148.somn.core.data.repository.SleepRepository;
import dev.vic41148.somn.core.domain.usecase.SmartAlarmUseCase;
import javax.annotation.processing.Generated;

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
public final class SleepTrackingService_MembersInjector implements MembersInjector<SleepTrackingService> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<AlarmRepository> alarmRepositoryProvider;

  private final Provider<SmartAlarmUseCase> smartAlarmUseCaseProvider;

  private SleepTrackingService_MembersInjector(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<AlarmRepository> alarmRepositoryProvider,
      Provider<SmartAlarmUseCase> smartAlarmUseCaseProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.alarmRepositoryProvider = alarmRepositoryProvider;
    this.smartAlarmUseCaseProvider = smartAlarmUseCaseProvider;
  }

  @Override
  public void injectMembers(SleepTrackingService instance) {
    injectSleepRepository(instance, sleepRepositoryProvider.get());
    injectAlarmRepository(instance, alarmRepositoryProvider.get());
    injectSmartAlarmUseCase(instance, smartAlarmUseCaseProvider.get());
  }

  public static MembersInjector<SleepTrackingService> create(
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<AlarmRepository> alarmRepositoryProvider,
      Provider<SmartAlarmUseCase> smartAlarmUseCaseProvider) {
    return new SleepTrackingService_MembersInjector(sleepRepositoryProvider, alarmRepositoryProvider, smartAlarmUseCaseProvider);
  }

  @InjectedFieldSignature("dev.vic41148.somn.feature.tracking.service.SleepTrackingService.sleepRepository")
  public static void injectSleepRepository(SleepTrackingService instance,
      SleepRepository sleepRepository) {
    instance.sleepRepository = sleepRepository;
  }

  @InjectedFieldSignature("dev.vic41148.somn.feature.tracking.service.SleepTrackingService.alarmRepository")
  public static void injectAlarmRepository(SleepTrackingService instance,
      AlarmRepository alarmRepository) {
    instance.alarmRepository = alarmRepository;
  }

  @InjectedFieldSignature("dev.vic41148.somn.feature.tracking.service.SleepTrackingService.smartAlarmUseCase")
  public static void injectSmartAlarmUseCase(SleepTrackingService instance,
      SmartAlarmUseCase smartAlarmUseCase) {
    instance.smartAlarmUseCase = smartAlarmUseCase;
  }
}
