package dev.vic41148.somn.core.data.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.SleepDatabase;
import dev.vic41148.somn.core.data.database.dao.SleepEpochDao;
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
public final class DataModule_ProvideSleepEpochDaoFactory implements Factory<SleepEpochDao> {
  private final Provider<SleepDatabase> dbProvider;

  private DataModule_ProvideSleepEpochDaoFactory(Provider<SleepDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public SleepEpochDao get() {
    return provideSleepEpochDao(dbProvider.get());
  }

  public static DataModule_ProvideSleepEpochDaoFactory create(Provider<SleepDatabase> dbProvider) {
    return new DataModule_ProvideSleepEpochDaoFactory(dbProvider);
  }

  public static SleepEpochDao provideSleepEpochDao(SleepDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideSleepEpochDao(db));
  }
}
