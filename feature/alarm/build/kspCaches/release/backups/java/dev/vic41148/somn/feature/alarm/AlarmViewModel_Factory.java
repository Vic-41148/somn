package dev.vic41148.somn.feature.alarm;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.repository.AlarmRepository;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class AlarmViewModel_Factory implements Factory<AlarmViewModel> {
  private final Provider<AlarmRepository> alarmRepositoryProvider;

  private AlarmViewModel_Factory(Provider<AlarmRepository> alarmRepositoryProvider) {
    this.alarmRepositoryProvider = alarmRepositoryProvider;
  }

  @Override
  public AlarmViewModel get() {
    return newInstance(alarmRepositoryProvider.get());
  }

  public static AlarmViewModel_Factory create(Provider<AlarmRepository> alarmRepositoryProvider) {
    return new AlarmViewModel_Factory(alarmRepositoryProvider);
  }

  public static AlarmViewModel newInstance(AlarmRepository alarmRepository) {
    return new AlarmViewModel(alarmRepository);
  }
}
