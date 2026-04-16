package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.SessionTagEntity
import dev.vic41148.somn.core.`data`.database.entity.TagEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TagDao_Impl(
  __db: RoomDatabase,
) : TagDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTagEntity: EntityInsertAdapter<TagEntity>

  private val __insertAdapterOfSessionTagEntity: EntityInsertAdapter<SessionTagEntity>

  private val __deleteAdapterOfTagEntity: EntityDeleteOrUpdateAdapter<TagEntity>

  private val __updateAdapterOfTagEntity: EntityDeleteOrUpdateAdapter<TagEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTagEntity = object : EntityInsertAdapter<TagEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `tags` (`id`,`name`,`category`,`color`,`icon`,`isArchived`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TagEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.category)
        statement.bindLong(4, entity.color)
        statement.bindText(5, entity.icon)
        val _tmp: Int = if (entity.isArchived) 1 else 0
        statement.bindLong(6, _tmp.toLong())
      }
    }
    this.__insertAdapterOfSessionTagEntity = object : EntityInsertAdapter<SessionTagEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `session_tags` (`sessionId`,`tagId`) VALUES (?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SessionTagEntity) {
        statement.bindLong(1, entity.sessionId)
        statement.bindLong(2, entity.tagId)
      }
    }
    this.__deleteAdapterOfTagEntity = object : EntityDeleteOrUpdateAdapter<TagEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `tags` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: TagEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfTagEntity = object : EntityDeleteOrUpdateAdapter<TagEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `tags` SET `id` = ?,`name` = ?,`category` = ?,`color` = ?,`icon` = ?,`isArchived` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: TagEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.category)
        statement.bindLong(4, entity.color)
        statement.bindText(5, entity.icon)
        val _tmp: Int = if (entity.isArchived) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        statement.bindLong(7, entity.id)
      }
    }
  }

  public override suspend fun insert(tag: TagEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfTagEntity.insertAndReturnId(_connection, tag)
    _result
  }

  public override suspend fun insertSessionTag(sessionTag: SessionTagEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSessionTagEntity.insert(_connection, sessionTag)
  }

  public override suspend fun delete(tag: TagEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfTagEntity.handle(_connection, tag)
  }

  public override suspend fun update(tag: TagEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfTagEntity.handle(_connection, tag)
  }

  public override fun observeAllActive(): Flow<List<TagEntity>> {
    val _sql: String = "SELECT * FROM tags WHERE isArchived = 0 ORDER BY category ASC, name ASC"
    return createFlow(__db, false, arrayOf("tags")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIcon: Int = getColumnIndexOrThrow(_stmt, "icon")
        val _columnIndexOfIsArchived: Int = getColumnIndexOrThrow(_stmt, "isArchived")
        val _result: MutableList<TagEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TagEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpColor: Long
          _tmpColor = _stmt.getLong(_columnIndexOfColor)
          val _tmpIcon: String
          _tmpIcon = _stmt.getText(_columnIndexOfIcon)
          val _tmpIsArchived: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsArchived).toInt()
          _tmpIsArchived = _tmp != 0
          _item = TagEntity(_tmpId,_tmpName,_tmpCategory,_tmpColor,_tmpIcon,_tmpIsArchived)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAll(): Flow<List<TagEntity>> {
    val _sql: String = "SELECT * FROM tags ORDER BY category ASC, name ASC"
    return createFlow(__db, false, arrayOf("tags")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIcon: Int = getColumnIndexOrThrow(_stmt, "icon")
        val _columnIndexOfIsArchived: Int = getColumnIndexOrThrow(_stmt, "isArchived")
        val _result: MutableList<TagEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TagEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpColor: Long
          _tmpColor = _stmt.getLong(_columnIndexOfColor)
          val _tmpIcon: String
          _tmpIcon = _stmt.getText(_columnIndexOfIcon)
          val _tmpIsArchived: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsArchived).toInt()
          _tmpIsArchived = _tmp != 0
          _item = TagEntity(_tmpId,_tmpName,_tmpCategory,_tmpColor,_tmpIcon,_tmpIsArchived)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeTagsForSession(sessionId: Long): Flow<List<TagEntity>> {
    val _sql: String = """
        |
        |        SELECT t.* FROM tags t 
        |        INNER JOIN session_tags st ON t.id = st.tagId 
        |        WHERE st.sessionId = ?
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("tags", "session_tags")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIcon: Int = getColumnIndexOrThrow(_stmt, "icon")
        val _columnIndexOfIsArchived: Int = getColumnIndexOrThrow(_stmt, "isArchived")
        val _result: MutableList<TagEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TagEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpColor: Long
          _tmpColor = _stmt.getLong(_columnIndexOfColor)
          val _tmpIcon: String
          _tmpIcon = _stmt.getText(_columnIndexOfIcon)
          val _tmpIsArchived: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsArchived).toInt()
          _tmpIsArchived = _tmp != 0
          _item = TagEntity(_tmpId,_tmpName,_tmpCategory,_tmpColor,_tmpIcon,_tmpIsArchived)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTagsForSession(sessionId: Long): List<TagEntity> {
    val _sql: String = """
        |
        |        SELECT t.* FROM tags t 
        |        INNER JOIN session_tags st ON t.id = st.tagId 
        |        WHERE st.sessionId = ?
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfIcon: Int = getColumnIndexOrThrow(_stmt, "icon")
        val _columnIndexOfIsArchived: Int = getColumnIndexOrThrow(_stmt, "isArchived")
        val _result: MutableList<TagEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TagEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpColor: Long
          _tmpColor = _stmt.getLong(_columnIndexOfColor)
          val _tmpIcon: String
          _tmpIcon = _stmt.getText(_columnIndexOfIcon)
          val _tmpIsArchived: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsArchived).toInt()
          _tmpIsArchived = _tmp != 0
          _item = TagEntity(_tmpId,_tmpName,_tmpCategory,_tmpColor,_tmpIcon,_tmpIsArchived)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun removeSessionTag(sessionId: Long, tagId: Long) {
    val _sql: String = "DELETE FROM session_tags WHERE sessionId = ? AND tagId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, tagId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
