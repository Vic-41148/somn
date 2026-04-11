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
