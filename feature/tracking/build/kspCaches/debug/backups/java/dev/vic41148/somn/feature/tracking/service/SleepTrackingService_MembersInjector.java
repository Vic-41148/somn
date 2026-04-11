package dev.vic41148.somn.feature.tracking.service;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dev.vic41148.somn.core.data.repository.SleepRepository;
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

  private SleepTrackingService_MembersInjector(Provider<SleepRepository> sleepRepositoryProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
  }

  @Override
  public void injectMembers(SleepTrackingService instance) {
    injectSleepRepository(instance, sleepRepositoryProvider.get());
  }

  public static MembersInjector<SleepTrackingService> create(
      Provider<SleepRepository> sleepRepositoryProvider) {
    return new SleepTrackingService_MembersInjector(sleepRepositoryProvider);
  }

  @InjectedFieldSignature("dev.vic41148.somn.feature.tracking.service.SleepTrackingService.sleepRepository")
  public static void injectSleepRepository(SleepTrackingService instance,
      SleepRepository sleepRepository) {
    instance.sleepRepository = sleepRepository;
  }
}
