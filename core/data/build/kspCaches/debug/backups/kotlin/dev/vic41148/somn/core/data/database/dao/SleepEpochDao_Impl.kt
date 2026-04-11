package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.SleepEpochEntity
import javax.`annotation`.processing.Generated
import kotlin.Float
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
public class SleepEpochDao_Impl(
  __db: RoomDatabase,
) : SleepEpochDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSleepEpochEntity: EntityInsertAdapter<SleepEpochEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSleepEpochEntity = object : EntityInsertAdapter<SleepEpochEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `sleep_epochs` (`id`,`sessionId`,`timestampMillis`,`stage`,`movementMagnitude`,`movementVariability`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SleepEpochEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.sessionId)
        statement.bindLong(3, entity.timestampMillis)
        statement.bindText(4, entity.stage)
        statement.bindDouble(5, entity.movementMagnitude.toDouble())
        statement.bindDouble(6, entity.movementVariability.toDouble())
      }
    }
  }

  public override suspend fun insert(epoch: SleepEpochEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfSleepEpochEntity.insertAndReturnId(_connection, epoch)
    _result
  }

  public override suspend fun insertAll(epochs: List<SleepEpochEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSleepEpochEntity.insert(_connection, epochs)
  }

  public override fun observeBySession(sessionId: Long): Flow<List<SleepEpochEntity>> {
    val _sql: String = "SELECT * FROM sleep_epochs WHERE sessionId = ? ORDER BY timestampMillis ASC"
    return createFlow(__db, false, arrayOf("sleep_epochs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfStage: Int = getColumnIndexOrThrow(_stmt, "stage")
        val _columnIndexOfMovementMagnitude: Int = getColumnIndexOrThrow(_stmt, "movementMagnitude")
        val _columnIndexOfMovementVariability: Int = getColumnIndexOrThrow(_stmt, "movementVariability")
        val _result: MutableList<SleepEpochEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SleepEpochEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpStage: String
          _tmpStage = _stmt.getText(_columnIndexOfStage)
          val _tmpMovementMagnitude: Float
          _tmpMovementMagnitude = _stmt.getDouble(_columnIndexOfMovementMagnitude).toFloat()
          val _tmpMovementVariability: Float
          _tmpMovementVariability = _stmt.getDouble(_columnIndexOfMovementVariability).toFloat()
          _item = SleepEpochEntity(_tmpId,_tmpSessionId,_tmpTimestampMillis,_tmpStage,_tmpMovementMagnitude,_tmpMovementVariability)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getBySession(sessionId: Long): List<SleepEpochEntity> {
    val _sql: String = "SELECT * FROM sleep_epochs WHERE sessionId = ? ORDER BY timestampMillis ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfStage: Int = getColumnIndexOrThrow(_stmt, "stage")
        val _columnIndexOfMovementMagnitude: Int = getColumnIndexOrThrow(_stmt, "movementMagnitude")
        val _columnIndexOfMovementVariability: Int = getColumnIndexOrThrow(_stmt, "movementVariability")
        val _result: MutableList<SleepEpochEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SleepEpochEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpStage: String
          _tmpStage = _stmt.getText(_columnIndexOfStage)
          val _tmpMovementMagnitude: Float
          _tmpMovementMagnitude = _stmt.getDouble(_columnIndexOfMovementMagnitude).toFloat()
          val _tmpMovementVariability: Float
          _tmpMovementVariability = _stmt.getDouble(_columnIndexOfMovementVariability).toFloat()
          _item = SleepEpochEntity(_tmpId,_tmpSessionId,_tmpTimestampMillis,_tmpStage,_tmpMovementMagnitude,_tmpMovementVariability)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLatestEpoch(sessionId: Long): SleepEpochEntity? {
    val _sql: String = "SELECT * FROM sleep_epochs WHERE sessionId = ? ORDER BY timestampMillis DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfStage: Int = getColumnIndexOrThrow(_stmt, "stage")
        val _columnIndexOfMovementMagnitude: Int = getColumnIndexOrThrow(_stmt, "movementMagnitude")
        val _columnIndexOfMovementVariability: Int = getColumnIndexOrThrow(_stmt, "movementVariability")
        val _result: SleepEpochEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpStage: String
          _tmpStage = _stmt.getText(_columnIndexOfStage)
          val _tmpMovementMagnitude: Float
          _tmpMovementMagnitude = _stmt.getDouble(_columnIndexOfMovementMagnitude).toFloat()
          val _tmpMovementVariability: Float
          _tmpMovementVariability = _stmt.getDouble(_columnIndexOfMovementVariability).toFloat()
          _result = SleepEpochEntity(_tmpId,_tmpSessionId,_tmpTimestampMillis,_tmpStage,_tmpMovementMagnitude,_tmpMovementVariability)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getRecentEpochs(sessionId: Long, count: Int): List<SleepEpochEntity> {
    val _sql: String = "SELECT * FROM sleep_epochs WHERE sessionId = ? ORDER BY timestampMillis DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, count.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSessionId: Int = getColumnIndexOrThrow(_stmt, "sessionId")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfStage: Int = getColumnIndexOrThrow(_stmt, "stage")
        val _columnIndexOfMovementMagnitude: Int = getColumnIndexOrThrow(_stmt, "movementMagnitude")
        val _columnIndexOfMovementVariability: Int = getColumnIndexOrThrow(_stmt, "movementVariability")
        val _result: MutableList<SleepEpochEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SleepEpochEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpSessionId: Long
          _tmpSessionId = _stmt.getLong(_columnIndexOfSessionId)
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpStage: String
          _tmpStage = _stmt.getText(_columnIndexOfStage)
          val _tmpMovementMagnitude: Float
          _tmpMovementMagnitude = _stmt.getDouble(_columnIndexOfMovementMagnitude).toFloat()
          val _tmpMovementVariability: Float
          _tmpMovementVariability = _stmt.getDouble(_columnIndexOfMovementVariability).toFloat()
          _item = SleepEpochEntity(_tmpId,_tmpSessionId,_tmpTimestampMillis,_tmpStage,_tmpMovementMagnitude,_tmpMovementVariability)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun countByStage(sessionId: Long, stage: String): Int {
    val _sql: String = "SELECT COUNT(*) FROM sleep_epochs WHERE sessionId = ? AND stage = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
        _argIndex = 2
        _stmt.bindText(_argIndex, stage)
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

  public override suspend fun deleteBySession(sessionId: Long) {
    val _sql: String = "DELETE FROM sleep_epochs WHERE sessionId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, sessionId)
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
