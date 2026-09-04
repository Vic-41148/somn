package dev.vic41148.somn.core.data.repository

import dev.vic41148.somn.core.data.database.dao.TagDao
import dev.vic41148.somn.core.data.database.entity.SessionTagEntity
import dev.vic41148.somn.core.data.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao
) {

    suspend fun createTag(name: String, category: String, color: Long, icon: String): Long {
        return tagDao.insert(TagEntity(name = name, category = category, color = color, icon = icon))
    }

    /**
     * R4 tag taxonomy: the built-in set Oura's 100+ tags gesture at, trimmed to a dozen
     * that actually move sleep. Idempotent — inserts only names the user doesn't already
     * have (custom or debug-seeded), so it is safe to call on every cold start.
     *
     * @return how many defaults were inserted.
     */
    suspend fun ensureDefaultTags(): Int {
        val existing = tagDao.getAllNames().toSet()
        var inserted = 0
        DEFAULT_TAGS.forEach { (name, category, icon) ->
            if (!existing.contains(name)) {
                tagDao.insert(TagEntity(name = name, category = category, icon = icon))
                inserted++
            }
        }
        return inserted
    }

    suspend fun updateTag(tag: TagEntity) {
        tagDao.update(tag)
    }

    suspend fun deleteTag(tag: TagEntity) {
        tagDao.delete(tag)
    }

    fun observeActiveTags(): Flow<List<TagEntity>> {
        return tagDao.observeAllActive()
    }

    fun observeAllTags(): Flow<List<TagEntity>> {
        return tagDao.observeAll()
    }

    suspend fun addTagToSession(sessionId: Long, tagId: Long) {
        tagDao.insertSessionTag(SessionTagEntity(sessionId, tagId))
    }

    suspend fun removeTagFromSession(sessionId: Long, tagId: Long) {
        tagDao.removeSessionTag(sessionId, tagId)
    }

    fun observeTagsForSession(sessionId: Long): Flow<List<TagEntity>> {
        return tagDao.observeTagsForSession(sessionId)
    }

    suspend fun getTagsForSession(sessionId: Long): List<TagEntity> {
        return tagDao.getTagsForSession(sessionId)
    }
}

/** Name, category, icon — colors stay the default pill tint. */
private val DEFAULT_TAGS = listOf(
    Triple("Late meal", "Food & drink", "dinner_dining"),
    Triple("Alcohol-free day", "Food & drink", "no_drinks"),
    Triple("Caffeine-free day", "Food & drink", "coffee_off"),
    Triple("Travel", "Environment", "flight"),
    Triple("Screen time", "Environment", "smartphone"),
    Triple("Sauna", "Recovery", "sauna"),
    Triple("Nap", "Recovery", "bedtime"),
    Triple("Melatonin", "Recovery", "medication"),
    Triple("Illness", "Health", "sick"),
    Triple("Pain", "Health", "healing"),
    Triple("Stressful day", "Mind", "psychology"),
    Triple("Wind-down", "Mind", "self_improvement")
)
