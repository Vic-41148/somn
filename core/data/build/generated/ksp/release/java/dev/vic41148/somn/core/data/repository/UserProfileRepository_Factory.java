package dev.vic41148.somn.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.dao.UserProfileDao;
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
public final class UserProfileRepository_Factory implements Factory<UserProfileRepository> {
  private final Provider<UserProfileDao> profileDaoProvider;

  private UserProfileRepository_Factory(Provider<UserProfileDao> profileDaoProvider) {
    this.profileDaoProvider = profileDaoProvider;
  }

  @Override
  public UserProfileRepository get() {
    return newInstance(profileDaoProvider.get());
  }

  public static UserProfileRepository_Factory create(Provider<UserProfileDao> profileDaoProvider) {
    return new UserProfileRepository_Factory(profileDaoProvider);
  }

  public static UserProfileRepository newInstance(UserProfileDao profileDao) {
    return new UserProfileRepository(profileDao);
  }
}
