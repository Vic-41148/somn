package dev.vic41148.somn.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.dao.HabitLogDao;
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
public final class HabitLogRepository_Factory implements Factory<HabitLogRepository> {
  private final Provider<HabitLogDao> daoProvider;

  private HabitLogRepository_Factory(Provider<HabitLogDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public HabitLogRepository get() {
    return newInstance(daoProvider.get());
  }

  public static HabitLogRepository_Factory create(Provider<HabitLogDao> daoProvider) {
    return new HabitLogRepository_Factory(daoProvider);
  }

  public static HabitLogRepository newInstance(HabitLogDao dao) {
    return new HabitLogRepository(dao);
  }
}
