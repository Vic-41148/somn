package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.AlarmEntity
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
public class AlarmDao_Impl(
  __db: RoomDatabase,
) : AlarmDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAlarmEntity: EntityInsertAdapter<AlarmEntity>

  private val __deleteAdapterOfAlarmEntity: EntityDeleteOrUpdateAdapter<AlarmEntity>

  private val __updateAdapterOfAlarmEntity: EntityDeleteOrUpdateAdapter<AlarmEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfAlarmEntity = object : EntityInsertAdapter<AlarmEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `alarms` (`id`,`hour`,`minute`,`label`,`isEnabled`,`repeatDays`,`wakeWindowMinutes`,`snoozeDurationMinutes`,`maxSnoozeCount`,`soundUri`,`vibrationEnabled`,`gradualVolumeSeconds`,`captchaType`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AlarmEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.hour.toLong())
        statement.bindLong(3, entity.minute.toLong())
        statement.bindText(4, entity.label)
        val _tmp: Int = if (entity.isEnabled) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindText(6, entity.repeatDays)
        statement.bindLong(7, entity.wakeWindowMinutes.toLong())
        statement.bindLong(8, entity.snoozeDurationMinutes.toLong())
        statement.bindLong(9, entity.maxSnoozeCount.toLong())
        statement.bindText(10, entity.soundUri)
        val _tmp_1: Int = if (entity.vibrationEnabled) 1 else 0
        statement.bindLong(11, _tmp_1.toLong())
        statement.bindLong(12, entity.gradualVolumeSeconds.toLong())
        statement.bindText(13, entity.captchaType)
      }
    }
    this.__deleteAdapterOfAlarmEntity = object : EntityDeleteOrUpdateAdapter<AlarmEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `alarms` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: AlarmEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfAlarmEntity = object : EntityDeleteOrUpdateAdapter<AlarmEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `alarms` SET `id` = ?,`hour` = ?,`minute` = ?,`label` = ?,`isEnabled` = ?,`repeatDays` = ?,`wakeWindowMinutes` = ?,`snoozeDurationMinutes` = ?,`maxSnoozeCount` = ?,`soundUri` = ?,`vibrationEnabled` = ?,`gradualVolumeSeconds` = ?,`captchaType` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: AlarmEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.hour.toLong())
        statement.bindLong(3, entity.minute.toLong())
        statement.bindText(4, entity.label)
        val _tmp: Int = if (entity.isEnabled) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindText(6, entity.repeatDays)
        statement.bindLong(7, entity.wakeWindowMinutes.toLong())
        statement.bindLong(8, entity.snoozeDurationMinutes.toLong())
        statement.bindLong(9, entity.maxSnoozeCount.toLong())
        statement.bindText(10, entity.soundUri)
        val _tmp_1: Int = if (entity.vibrationEnabled) 1 else 0
        statement.bindLong(11, _tmp_1.toLong())
        statement.bindLong(12, entity.gradualVolumeSeconds.toLong())
        statement.bindText(13, entity.captchaType)
        statement.bindLong(14, entity.id)
      }
    }
  }

  public override suspend fun insert(alarm: AlarmEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfAlarmEntity.insertAndReturnId(_connection, alarm)
    _result
  }

  public override suspend fun delete(alarm: AlarmEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfAlarmEntity.handle(_connection, alarm)
  }

  public override suspend fun update(alarm: AlarmEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfAlarmEntity.handle(_connection, alarm)
  }

  public override fun observeAll(): Flow<List<AlarmEntity>> {
    val _sql: String = "SELECT * FROM alarms ORDER BY hour ASC, minute ASC"
    return createFlow(__db, false, arrayOf("alarms")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfHour: Int = getColumnIndexOrThrow(_stmt, "hour")
        val _columnIndexOfMinute: Int = getColumnIndexOrThrow(_stmt, "minute")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfIsEnabled: Int = getColumnIndexOrThrow(_stmt, "isEnabled")
        val _columnIndexOfRepeatDays: Int = getColumnIndexOrThrow(_stmt, "repeatDays")
        val _columnIndexOfWakeWindowMinutes: Int = getColumnIndexOrThrow(_stmt, "wakeWindowMinutes")
        val _columnIndexOfSnoozeDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "snoozeDurationMinutes")
        val _columnIndexOfMaxSnoozeCount: Int = getColumnIndexOrThrow(_stmt, "maxSnoozeCount")
        val _columnIndexOfSoundUri: Int = getColumnIndexOrThrow(_stmt, "soundUri")
        val _columnIndexOfVibrationEnabled: Int = getColumnIndexOrThrow(_stmt, "vibrationEnabled")
        val _columnIndexOfGradualVolumeSeconds: Int = getColumnIndexOrThrow(_stmt, "gradualVolumeSeconds")
        val _columnIndexOfCaptchaType: Int = getColumnIndexOrThrow(_stmt, "captchaType")
        val _result: MutableList<AlarmEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AlarmEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpHour: Int
          _tmpHour = _stmt.getLong(_columnIndexOfHour).toInt()
          val _tmpMinute: Int
          _tmpMinute = _stmt.getLong(_columnIndexOfMinute).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpIsEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsEnabled).toInt()
          _tmpIsEnabled = _tmp != 0
          val _tmpRepeatDays: String
          _tmpRepeatDays = _stmt.getText(_columnIndexOfRepeatDays)
          val _tmpWakeWindowMinutes: Int
          _tmpWakeWindowMinutes = _stmt.getLong(_columnIndexOfWakeWindowMinutes).toInt()
          val _tmpSnoozeDurationMinutes: Int
          _tmpSnoozeDurationMinutes = _stmt.getLong(_columnIndexOfSnoozeDurationMinutes).toInt()
          val _tmpMaxSnoozeCount: Int
          _tmpMaxSnoozeCount = _stmt.getLong(_columnIndexOfMaxSnoozeCount).toInt()
          val _tmpSoundUri: String
          _tmpSoundUri = _stmt.getText(_columnIndexOfSoundUri)
          val _tmpVibrationEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfVibrationEnabled).toInt()
          _tmpVibrationEnabled = _tmp_1 != 0
          val _tmpGradualVolumeSeconds: Int
          _tmpGradualVolumeSeconds = _stmt.getLong(_columnIndexOfGradualVolumeSeconds).toInt()
          val _tmpCaptchaType: String
          _tmpCaptchaType = _stmt.getText(_columnIndexOfCaptchaType)
          _item = AlarmEntity(_tmpId,_tmpHour,_tmpMinute,_tmpLabel,_tmpIsEnabled,_tmpRepeatDays,_tmpWakeWindowMinutes,_tmpSnoozeDurationMinutes,_tmpMaxSnoozeCount,_tmpSoundUri,_tmpVibrationEnabled,_tmpGradualVolumeSeconds,_tmpCaptchaType)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): AlarmEntity? {
    val _sql: String = "SELECT * FROM alarms WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfHour: Int = getColumnIndexOrThrow(_stmt, "hour")
        val _columnIndexOfMinute: Int = getColumnIndexOrThrow(_stmt, "minute")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfIsEnabled: Int = getColumnIndexOrThrow(_stmt, "isEnabled")
        val _columnIndexOfRepeatDays: Int = getColumnIndexOrThrow(_stmt, "repeatDays")
        val _columnIndexOfWakeWindowMinutes: Int = getColumnIndexOrThrow(_stmt, "wakeWindowMinutes")
        val _columnIndexOfSnoozeDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "snoozeDurationMinutes")
        val _columnIndexOfMaxSnoozeCount: Int = getColumnIndexOrThrow(_stmt, "maxSnoozeCount")
        val _columnIndexOfSoundUri: Int = getColumnIndexOrThrow(_stmt, "soundUri")
        val _columnIndexOfVibrationEnabled: Int = getColumnIndexOrThrow(_stmt, "vibrationEnabled")
        val _columnIndexOfGradualVolumeSeconds: Int = getColumnIndexOrThrow(_stmt, "gradualVolumeSeconds")
        val _columnIndexOfCaptchaType: Int = getColumnIndexOrThrow(_stmt, "captchaType")
        val _result: AlarmEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpHour: Int
          _tmpHour = _stmt.getLong(_columnIndexOfHour).toInt()
          val _tmpMinute: Int
          _tmpMinute = _stmt.getLong(_columnIndexOfMinute).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpIsEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsEnabled).toInt()
          _tmpIsEnabled = _tmp != 0
          val _tmpRepeatDays: String
          _tmpRepeatDays = _stmt.getText(_columnIndexOfRepeatDays)
          val _tmpWakeWindowMinutes: Int
          _tmpWakeWindowMinutes = _stmt.getLong(_columnIndexOfWakeWindowMinutes).toInt()
          val _tmpSnoozeDurationMinutes: Int
          _tmpSnoozeDurationMinutes = _stmt.getLong(_columnIndexOfSnoozeDurationMinutes).toInt()
          val _tmpMaxSnoozeCount: Int
          _tmpMaxSnoozeCount = _stmt.getLong(_columnIndexOfMaxSnoozeCount).toInt()
          val _tmpSoundUri: String
          _tmpSoundUri = _stmt.getText(_columnIndexOfSoundUri)
          val _tmpVibrationEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfVibrationEnabled).toInt()
          _tmpVibrationEnabled = _tmp_1 != 0
          val _tmpGradualVolumeSeconds: Int
          _tmpGradualVolumeSeconds = _stmt.getLong(_columnIndexOfGradualVolumeSeconds).toInt()
          val _tmpCaptchaType: String
          _tmpCaptchaType = _stmt.getText(_columnIndexOfCaptchaType)
          _result = AlarmEntity(_tmpId,_tmpHour,_tmpMinute,_tmpLabel,_tmpIsEnabled,_tmpRepeatDays,_tmpWakeWindowMinutes,_tmpSnoozeDurationMinutes,_tmpMaxSnoozeCount,_tmpSoundUri,_tmpVibrationEnabled,_tmpGradualVolumeSeconds,_tmpCaptchaType)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getEnabledAlarms(): List<AlarmEntity> {
    val _sql: String = "SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfHour: Int = getColumnIndexOrThrow(_stmt, "hour")
        val _columnIndexOfMinute: Int = getColumnIndexOrThrow(_stmt, "minute")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfIsEnabled: Int = getColumnIndexOrThrow(_stmt, "isEnabled")
        val _columnIndexOfRepeatDays: Int = getColumnIndexOrThrow(_stmt, "repeatDays")
        val _columnIndexOfWakeWindowMinutes: Int = getColumnIndexOrThrow(_stmt, "wakeWindowMinutes")
        val _columnIndexOfSnoozeDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "snoozeDurationMinutes")
        val _columnIndexOfMaxSnoozeCount: Int = getColumnIndexOrThrow(_stmt, "maxSnoozeCount")
        val _columnIndexOfSoundUri: Int = getColumnIndexOrThrow(_stmt, "soundUri")
        val _columnIndexOfVibrationEnabled: Int = getColumnIndexOrThrow(_stmt, "vibrationEnabled")
        val _columnIndexOfGradualVolumeSeconds: Int = getColumnIndexOrThrow(_stmt, "gradualVolumeSeconds")
        val _columnIndexOfCaptchaType: Int = getColumnIndexOrThrow(_stmt, "captchaType")
        val _result: MutableList<AlarmEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AlarmEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpHour: Int
          _tmpHour = _stmt.getLong(_columnIndexOfHour).toInt()
          val _tmpMinute: Int
          _tmpMinute = _stmt.getLong(_columnIndexOfMinute).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpIsEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsEnabled).toInt()
          _tmpIsEnabled = _tmp != 0
          val _tmpRepeatDays: String
          _tmpRepeatDays = _stmt.getText(_columnIndexOfRepeatDays)
          val _tmpWakeWindowMinutes: Int
          _tmpWakeWindowMinutes = _stmt.getLong(_columnIndexOfWakeWindowMinutes).toInt()
          val _tmpSnoozeDurationMinutes: Int
          _tmpSnoozeDurationMinutes = _stmt.getLong(_columnIndexOfSnoozeDurationMinutes).toInt()
          val _tmpMaxSnoozeCount: Int
          _tmpMaxSnoozeCount = _stmt.getLong(_columnIndexOfMaxSnoozeCount).toInt()
          val _tmpSoundUri: String
          _tmpSoundUri = _stmt.getText(_columnIndexOfSoundUri)
          val _tmpVibrationEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfVibrationEnabled).toInt()
          _tmpVibrationEnabled = _tmp_1 != 0
          val _tmpGradualVolumeSeconds: Int
          _tmpGradualVolumeSeconds = _stmt.getLong(_columnIndexOfGradualVolumeSeconds).toInt()
          val _tmpCaptchaType: String
          _tmpCaptchaType = _stmt.getText(_columnIndexOfCaptchaType)
          _item = AlarmEntity(_tmpId,_tmpHour,_tmpMinute,_tmpLabel,_tmpIsEnabled,_tmpRepeatDays,_tmpWakeWindowMinutes,_tmpSnoozeDurationMinutes,_tmpMaxSnoozeCount,_tmpSoundUri,_tmpVibrationEnabled,_tmpGradualVolumeSeconds,_tmpCaptchaType)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getNextAlarm(): AlarmEntity? {
    val _sql: String = "SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfHour: Int = getColumnIndexOrThrow(_stmt, "hour")
        val _columnIndexOfMinute: Int = getColumnIndexOrThrow(_stmt, "minute")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfIsEnabled: Int = getColumnIndexOrThrow(_stmt, "isEnabled")
        val _columnIndexOfRepeatDays: Int = getColumnIndexOrThrow(_stmt, "repeatDays")
        val _columnIndexOfWakeWindowMinutes: Int = getColumnIndexOrThrow(_stmt, "wakeWindowMinutes")
        val _columnIndexOfSnoozeDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "snoozeDurationMinutes")
        val _columnIndexOfMaxSnoozeCount: Int = getColumnIndexOrThrow(_stmt, "maxSnoozeCount")
        val _columnIndexOfSoundUri: Int = getColumnIndexOrThrow(_stmt, "soundUri")
        val _columnIndexOfVibrationEnabled: Int = getColumnIndexOrThrow(_stmt, "vibrationEnabled")
        val _columnIndexOfGradualVolumeSeconds: Int = getColumnIndexOrThrow(_stmt, "gradualVolumeSeconds")
        val _columnIndexOfCaptchaType: Int = getColumnIndexOrThrow(_stmt, "captchaType")
        val _result: AlarmEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpHour: Int
          _tmpHour = _stmt.getLong(_columnIndexOfHour).toInt()
          val _tmpMinute: Int
          _tmpMinute = _stmt.getLong(_columnIndexOfMinute).toInt()
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpIsEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsEnabled).toInt()
          _tmpIsEnabled = _tmp != 0
          val _tmpRepeatDays: String
          _tmpRepeatDays = _stmt.getText(_columnIndexOfRepeatDays)
          val _tmpWakeWindowMinutes: Int
          _tmpWakeWindowMinutes = _stmt.getLong(_columnIndexOfWakeWindowMinutes).toInt()
          val _tmpSnoozeDurationMinutes: Int
          _tmpSnoozeDurationMinutes = _stmt.getLong(_columnIndexOfSnoozeDurationMinutes).toInt()
          val _tmpMaxSnoozeCount: Int
          _tmpMaxSnoozeCount = _stmt.getLong(_columnIndexOfMaxSnoozeCount).toInt()
          val _tmpSoundUri: String
          _tmpSoundUri = _stmt.getText(_columnIndexOfSoundUri)
          val _tmpVibrationEnabled: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfVibrationEnabled).toInt()
          _tmpVibrationEnabled = _tmp_1 != 0
          val _tmpGradualVolumeSeconds: Int
          _tmpGradualVolumeSeconds = _stmt.getLong(_columnIndexOfGradualVolumeSeconds).toInt()
          val _tmpCaptchaType: String
          _tmpCaptchaType = _stmt.getText(_columnIndexOfCaptchaType)
          _result = AlarmEntity(_tmpId,_tmpHour,_tmpMinute,_tmpLabel,_tmpIsEnabled,_tmpRepeatDays,_tmpWakeWindowMinutes,_tmpSnoozeDurationMinutes,_tmpMaxSnoozeCount,_tmpSoundUri,_tmpVibrationEnabled,_tmpGradualVolumeSeconds,_tmpCaptchaType)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setEnabled(id: Long, enabled: Boolean) {
    val _sql: String = "UPDATE alarms SET isEnabled = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (enabled) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
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
