package dev.vic41148.somn.feature.alarm.service;

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
public final class AlarmService_MembersInjector implements MembersInjector<AlarmService> {
  private final Provider<UserProfileRepository> userProfileRepositoryProvider;

  private AlarmService_MembersInjector(
      Provider<UserProfileRepository> userProfileRepositoryProvider) {
    this.userProfileRepositoryProvider = userProfileRepositoryProvider;
  }

  @Override
  public void injectMembers(AlarmService instance) {
    injectUserProfileRepository(instance, userProfileRepositoryProvider.get());
  }

  public static MembersInjector<AlarmService> create(
      Provider<UserProfileRepository> userProfileRepositoryProvider) {
    return new AlarmService_MembersInjector(userProfileRepositoryProvider);
  }

  @InjectedFieldSignature("dev.vic41148.somn.feature.alarm.service.AlarmService.userProfileRepository")
  public static void injectUserProfileRepository(AlarmService instance,
      UserProfileRepository userProfileRepository) {
    instance.userProfileRepository = userProfileRepository;
  }
}
