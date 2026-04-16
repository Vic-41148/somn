package dev.vic41148.somn.core.data.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.SleepDatabase;
import dev.vic41148.somn.core.data.database.dao.AlarmDao;
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
public final class DataModule_ProvideAlarmDaoFactory implements Factory<AlarmDao> {
  private final Provider<SleepDatabase> dbProvider;

  private DataModule_ProvideAlarmDaoFactory(Provider<SleepDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public AlarmDao get() {
    return provideAlarmDao(dbProvider.get());
  }

  public static DataModule_ProvideAlarmDaoFactory create(Provider<SleepDatabase> dbProvider) {
    return new DataModule_ProvideAlarmDaoFactory(dbProvider);
  }

  public static AlarmDao provideAlarmDao(SleepDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideAlarmDao(db));
  }
}
