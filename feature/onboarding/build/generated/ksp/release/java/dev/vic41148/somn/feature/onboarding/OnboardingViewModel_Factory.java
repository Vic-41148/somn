package dev.vic41148.somn.feature.onboarding;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.repository.UserProfileRepository;
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
public final class OnboardingViewModel_Factory implements Factory<OnboardingViewModel> {
  private final Provider<UserProfileRepository> profileRepositoryProvider;

  private OnboardingViewModel_Factory(Provider<UserProfileRepository> profileRepositoryProvider) {
    this.profileRepositoryProvider = profileRepositoryProvider;
  }

  @Override
  public OnboardingViewModel get() {
    return newInstance(profileRepositoryProvider.get());
  }

  public static OnboardingViewModel_Factory create(
      Provider<UserProfileRepository> profileRepositoryProvider) {
    return new OnboardingViewModel_Factory(profileRepositoryProvider);
  }

  public static OnboardingViewModel newInstance(UserProfileRepository profileRepository) {
    return new OnboardingViewModel(profileRepository);
  }
}
