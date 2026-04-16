package dev.vic41148.somn.core.data.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.SleepDatabase;
import dev.vic41148.somn.core.data.database.dao.HabitLogDao;
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
public final class DataModule_ProvideHabitLogDaoFactory implements Factory<HabitLogDao> {
  private final Provider<SleepDatabase> dbProvider;

  private DataModule_ProvideHabitLogDaoFactory(Provider<SleepDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public HabitLogDao get() {
    return provideHabitLogDao(dbProvider.get());
  }

  public static DataModule_ProvideHabitLogDaoFactory create(Provider<SleepDatabase> dbProvider) {
    return new DataModule_ProvideHabitLogDaoFactory(dbProvider);
  }

  public static HabitLogDao provideHabitLogDao(SleepDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideHabitLogDao(db));
  }
}
