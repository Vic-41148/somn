package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.AudioEventEntity
import javax.`annotation`.processing.Generated
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
public class AudioEventDao_Impl(
  __db: RoomDatabase,
) : AudioEventDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAudioEventEntity: EntityInsertAdapter<AudioEventEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfAudioEventEntity = object : EntityInsertAdapter<AudioEventEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `audio_events` (`id`,`sessionId`,`timestampMillis`,`durationSeconds`,`type`,`intensityDecibels`,`clipPath`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AudioEventEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.sessionId)
        statement.bindLong(3, entity.timestampMillis)
        statement.bindLong(4, entity.durationSeconds.toLong())
        statement.bindText(5, entity.type)
        statement.bindLong(6, entity.intensityDecibels.toLong())
        val _tmpClipPath: String? = entity.clipPath
        if (_tmpClipPath == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpClipPath)
        }
      }
    }
  }

  public override suspend fun insert(event: AudioEventEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfAudioEventEntity.insertAndReturnId(_connection, event)
    _result
  }

  public override suspend fun insertAll(events: List<AudioEventEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAudioEventEntity.insert(_connection, events)
  }

  public override fun observeBySession(sessionId: Long): Flow<List<AudioEventEntity>> {
    val _sql: String = "SELECT * FROM audio_events WHERE sessionId = ? ORDER BY timestampMillis ASC"
    return createFlow(__db, false, arrayOf("audio_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfDurationSeconds: Int = getColumnIndexOrThrow(_stmt, "durationSeconds")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfIntensityDecibels: Int = getColumnIndexOrThrow(_stmt, "intensityDecibels")
        val _columnIndexOfClipPath: Int = getColumnIndexOrThrow(_stmt, "clipPath")
        val _result: MutableList<AudioEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AudioEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpDurationSeconds: Int
          _tmpDurationSeconds = _stmt.getLong(_columnIndexOfDurationSeconds).toInt()
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpIntensityDecibels: Int
          _tmpIntensityDecibels = _stmt.getLong(_columnIndexOfIntensityDecibels).toInt()
          val _tmpClipPath: String?
          if (_stmt.isNull(_columnIndexOfClipPath)) {
            _tmpClipPath = null
          } else {
            _tmpClipPath = _stmt.getText(_columnIndexOfClipPath)
          }
          _item = AudioEventEntity(_tmpId,_tmpSessionId,_tmpTimestampMillis,_tmpDurationSeconds,_tmpType,_tmpIntensityDecibels,_tmpClipPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getBySession(sessionId: Long): List<AudioEventEntity> {
    val _sql: String = "SELECT * FROM audio_events WHERE sessionId = ? ORDER BY timestampMillis ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfDurationSeconds: Int = getColumnIndexOrThrow(_stmt, "durationSeconds")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfIntensityDecibels: Int = getColumnIndexOrThrow(_stmt, "intensityDecibels")
        val _columnIndexOfClipPath: Int = getColumnIndexOrThrow(_stmt, "clipPath")
        val _result: MutableList<AudioEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AudioEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpDurationSeconds: Int
          _tmpDurationSeconds = _stmt.getLong(_columnIndexOfDurationSeconds).toInt()
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpIntensityDecibels: Int
          _tmpIntensityDecibels = _stmt.getLong(_columnIndexOfIntensityDecibels).toInt()
          val _tmpClipPath: String?
          if (_stmt.isNull(_columnIndexOfClipPath)) {
            _tmpClipPath = null
          } else {
            _tmpClipPath = _stmt.getText(_columnIndexOfClipPath)
          }
          _item = AudioEventEntity(_tmpId,_tmpSessionId,_tmpTimestampMillis,_tmpDurationSeconds,_tmpType,_tmpIntensityDecibels,_tmpClipPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCountBySessionAndType(sessionId: Long, type: String): Int {
    val _sql: String = "SELECT COUNT(*) FROM audio_events WHERE sessionId = ? AND type = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        _argIndex = 2
        _stmt.bindText(_argIndex, type)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
