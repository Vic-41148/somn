package dev.vic41148.somn.app;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dev.vic41148.somn.core.data.repository.UserProfileRepository;
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<UserProfileRepository> profileRepositoryProvider;

  private MainActivity_MembersInjector(Provider<UserProfileRepository> profileRepositoryProvider) {
    this.profileRepositoryProvider = profileRepositoryProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectProfileRepository(instance, profileRepositoryProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<UserProfileRepository> profileRepositoryProvider) {
    return new MainActivity_MembersInjector(profileRepositoryProvider);
  }

  @InjectedFieldSignature("dev.vic41148.somn.app.MainActivity.profileRepository")
  public static void injectProfileRepository(MainActivity instance,
      UserProfileRepository profileRepository) {
    instance.profileRepository = profileRepository;
  }
}
