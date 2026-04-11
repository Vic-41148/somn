package dev.vic41148.somn.core.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import dev.vic41148.somn.core.data.database.dao.TagDao;
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
public final class TagRepository_Factory implements Factory<TagRepository> {
  private final Provider<TagDao> tagDaoProvider;

  private TagRepository_Factory(Provider<TagDao> tagDaoProvider) {
    this.tagDaoProvider = tagDaoProvider;
  }

  @Override
  public TagRepository get() {
    return newInstance(tagDaoProvider.get());
  }

  public static TagRepository_Factory create(Provider<TagDao> tagDaoProvider) {
    return new TagRepository_Factory(tagDaoProvider);
  }

  public static TagRepository newInstance(TagDao tagDao) {
    return new TagRepository(tagDao);
  }
}
