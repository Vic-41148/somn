package dev.vic41148.somn.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.dao.AlarmDao;
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
public final class AlarmRepository_Factory implements Factory<AlarmRepository> {
  private final Provider<AlarmDao> alarmDaoProvider;

  private AlarmRepository_Factory(Provider<AlarmDao> alarmDaoProvider) {
    this.alarmDaoProvider = alarmDaoProvider;
  }

  @Override
  public AlarmRepository get() {
    return newInstance(alarmDaoProvider.get());
  }

  public static AlarmRepository_Factory create(Provider<AlarmDao> alarmDaoProvider) {
    return new AlarmRepository_Factory(alarmDaoProvider);
  }

  public static AlarmRepository newInstance(AlarmDao alarmDao) {
    return new AlarmRepository(alarmDao);
  }
}
