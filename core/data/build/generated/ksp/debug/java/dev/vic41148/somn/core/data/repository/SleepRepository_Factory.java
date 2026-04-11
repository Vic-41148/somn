package dev.vic41148.somn.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.dao.SleepEpochDao;
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao;
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
public final class SleepRepository_Factory implements Factory<SleepRepository> {
  private final Provider<SleepSessionDao> sessionDaoProvider;

  private final Provider<SleepEpochDao> epochDaoProvider;

  private SleepRepository_Factory(Provider<SleepSessionDao> sessionDaoProvider,
      Provider<SleepEpochDao> epochDaoProvider) {
    this.sessionDaoProvider = sessionDaoProvider;
    this.epochDaoProvider = epochDaoProvider;
  }

  @Override
  public SleepRepository get() {
    return newInstance(sessionDaoProvider.get(), epochDaoProvider.get());
  }

  public static SleepRepository_Factory create(Provider<SleepSessionDao> sessionDaoProvider,
      Provider<SleepEpochDao> epochDaoProvider) {
    return new SleepRepository_Factory(sessionDaoProvider, epochDaoProvider);
  }

  public static SleepRepository newInstance(SleepSessionDao sessionDao, SleepEpochDao epochDao) {
    return new SleepRepository(sessionDao, epochDao);
  }
}
