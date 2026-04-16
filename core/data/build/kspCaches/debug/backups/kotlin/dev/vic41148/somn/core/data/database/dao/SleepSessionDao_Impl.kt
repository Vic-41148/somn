package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.SleepSessionEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class SleepSessionDao_Impl(
  __db: RoomDatabase,
) : SleepSessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSleepSessionEntity: EntityInsertAdapter<SleepSessionEntity>

  private val __deleteAdapterOfSleepSessionEntity: EntityDeleteOrUpdateAdapter<SleepSessionEntity>

  private val __updateAdapterOfSleepSessionEntity: EntityDeleteOrUpdateAdapter<SleepSessionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSleepSessionEntity = object : EntityInsertAdapter<SleepSessionEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `sleep_sessions` (`id`,`startTimeMillis`,`endTimeMillis`,`sleepDurationMinutes`,`timeInBedMinutes`,`sleepEfficiency`,`sleepOnsetMinutes`,`wakeEvents`,`deepSleepPercent`,`lightSleepPercent`,`remSleepPercent`,`sleepScore`,`moodRating`,`notes`,`isCompleted`,`timezoneId`,`isHomeSleep`,`alarmUsed`,`avgBreathingRateBrpm`,`coughEventCount`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SleepSessionEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.startTimeMillis)
        statement.bindLong(3, entity.endTimeMillis)
        statement.bindLong(4, entity.sleepDurationMinutes.toLong())
        statement.bindLong(5, entity.timeInBedMinutes.toLong())
        statement.bindDouble(6, entity.sleepEfficiency.toDouble())
        statement.bindLong(7, entity.sleepOnsetMinutes.toLong())
        statement.bindLong(8, entity.wakeEvents.toLong())
        statement.bindDouble(9, entity.deepSleepPercent.toDouble())
        statement.bindDouble(10, entity.lightSleepPercent.toDouble())
        statement.bindDouble(11, entity.remSleepPercent.toDouble())
        statement.bindLong(12, entity.sleepScore.toLong())
        statement.bindLong(13, entity.moodRating.toLong())
        statement.bindText(14, entity.notes)
        val _tmp: Int = if (entity.isCompleted) 1 else 0
        statement.bindLong(15, _tmp.toLong())
        statement.bindText(16, entity.timezoneId)
        val _tmp_1: Int = if (entity.isHomeSleep) 1 else 0
        statement.bindLong(17, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.alarmUsed) 1 else 0
        statement.bindLong(18, _tmp_2.toLong())
        val _tmpAvgBreathingRateBrpm: Float? = entity.avgBreathingRateBrpm
        if (_tmpAvgBreathingRateBrpm == null) {
          statement.bindNull(19)
        } else {
          statement.bindDouble(19, _tmpAvgBreathingRateBrpm.toDouble())
        }
        statement.bindLong(20, entity.coughEventCount.toLong())
      }
    }
    this.__deleteAdapterOfSleepSessionEntity = object : EntityDeleteOrUpdateAdapter<SleepSessionEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `sleep_sessions` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SleepSessionEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfSleepSessionEntity = object : EntityDeleteOrUpdateAdapter<SleepSessionEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `sleep_sessions` SET `id` = ?,`startTimeMillis` = ?,`endTimeMillis` = ?,`sleepDurationMinutes` = ?,`timeInBedMinutes` = ?,`sleepEfficiency` = ?,`sleepOnsetMinutes` = ?,`wakeEvents` = ?,`deepSleepPercent` = ?,`lightSleepPercent` = ?,`remSleepPercent` = ?,`sleepScore` = ?,`moodRating` = ?,`notes` = ?,`isCompleted` = ?,`timezoneId` = ?,`isHomeSleep` = ?,`alarmUsed` = ?,`avgBreathingRateBrpm` = ?,`coughEventCount` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SleepSessionEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.startTimeMillis)
        statement.bindLong(3, entity.endTimeMillis)
        statement.bindLong(4, entity.sleepDurationMinutes.toLong())
        statement.bindLong(5, entity.timeInBedMinutes.toLong())
        statement.bindDouble(6, entity.sleepEfficiency.toDouble())
        statement.bindLong(7, entity.sleepOnsetMinutes.toLong())
        statement.bindLong(8, entity.wakeEvents.toLong())
        statement.bindDouble(9, entity.deepSleepPercent.toDouble())
        statement.bindDouble(10, entity.lightSleepPercent.toDouble())
        statement.bindDouble(11, entity.remSleepPercent.toDouble())
        statement.bindLong(12, entity.sleepScore.toLong())
        statement.bindLong(13, entity.moodRating.toLong())
        statement.bindText(14, entity.notes)
        val _tmp: Int = if (entity.isCompleted) 1 else 0
        statement.bindLong(15, _tmp.toLong())
        statement.bindText(16, entity.timezoneId)
        val _tmp_1: Int = if (entity.isHomeSleep) 1 else 0
        statement.bindLong(17, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.alarmUsed) 1 else 0
        statement.bindLong(18, _tmp_2.toLong())
        val _tmpAvgBreathingRateBrpm: Float? = entity.avgBreathingRateBrpm
        if (_tmpAvgBreathingRateBrpm == null) {
          statement.bindNull(19)
        } else {
          statement.bindDouble(19, _tmpAvgBreathingRateBrpm.toDouble())
        }
        statement.bindLong(20, entity.coughEventCount.toLong())
        statement.bindLong(21, entity.id)
      }
    }
  }

  public override suspend fun insert(session: SleepSessionEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfSleepSessionEntity.insertAndReturnId(_connection, session)
    _result
  }

  public override suspend fun delete(session: SleepSessionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfSleepSessionEntity.handle(_connection, session)
  }

  public override suspend fun update(session: SleepSessionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfSleepSessionEntity.handle(_connection, session)
  }

  public override suspend fun getById(id: Long): SleepSessionEntity? {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: SleepSessionEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _result = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeById(id: Long): Flow<SleepSessionEntity?> {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE id = ?"
    return createFlow(__db, false, arrayOf("sleep_sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: SleepSessionEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _result = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeAllCompleted(): Flow<List<SleepSessionEntity>> {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE isCompleted = 1 ORDER BY startTimeMillis DESC"
    return createFlow(__db, false, arrayOf("sleep_sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: MutableList<SleepSessionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SleepSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _item = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getActiveSession(): SleepSessionEntity? {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE isCompleted = 0 LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: SleepSessionEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _result = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeActiveSession(): Flow<SleepSessionEntity?> {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE isCompleted = 0 LIMIT 1"
    return createFlow(__db, false, arrayOf("sleep_sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: SleepSessionEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _result = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getRecentSessions(limit: Int): List<SleepSessionEntity> {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE isCompleted = 1 ORDER BY startTimeMillis DESC LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: MutableList<SleepSessionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SleepSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _item = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSessionsSince(fromMillis: Long): List<SleepSessionEntity> {
    val _sql: String = "SELECT * FROM sleep_sessions WHERE isCompleted = 1 AND startTimeMillis >= ? ORDER BY startTimeMillis DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, fromMillis)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTimeMillis: Int = getColumnIndexOrThrow(_stmt, "startTimeMillis")
        val _columnIndexOfEndTimeMillis: Int = getColumnIndexOrThrow(_stmt, "endTimeMillis")
        val _columnIndexOfSleepDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepDurationMinutes")
        val _columnIndexOfTimeInBedMinutes: Int = getColumnIndexOrThrow(_stmt, "timeInBedMinutes")
        val _columnIndexOfSleepEfficiency: Int = getColumnIndexOrThrow(_stmt, "sleepEfficiency")
        val _columnIndexOfSleepOnsetMinutes: Int = getColumnIndexOrThrow(_stmt, "sleepOnsetMinutes")
        val _columnIndexOfWakeEvents: Int = getColumnIndexOrThrow(_stmt, "wakeEvents")
        val _columnIndexOfDeepSleepPercent: Int = getColumnIndexOrThrow(_stmt, "deepSleepPercent")
        val _columnIndexOfLightSleepPercent: Int = getColumnIndexOrThrow(_stmt, "lightSleepPercent")
        val _columnIndexOfRemSleepPercent: Int = getColumnIndexOrThrow(_stmt, "remSleepPercent")
        val _columnIndexOfSleepScore: Int = getColumnIndexOrThrow(_stmt, "sleepScore")
        val _columnIndexOfMoodRating: Int = getColumnIndexOrThrow(_stmt, "moodRating")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfIsCompleted: Int = getColumnIndexOrThrow(_stmt, "isCompleted")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfIsHomeSleep: Int = getColumnIndexOrThrow(_stmt, "isHomeSleep")
        val _columnIndexOfAlarmUsed: Int = getColumnIndexOrThrow(_stmt, "alarmUsed")
        val _columnIndexOfAvgBreathingRateBrpm: Int = getColumnIndexOrThrow(_stmt, "avgBreathingRateBrpm")
        val _columnIndexOfCoughEventCount: Int = getColumnIndexOrThrow(_stmt, "coughEventCount")
        val _result: MutableList<SleepSessionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SleepSessionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpStartTimeMillis: Long
          _tmpStartTimeMillis = _stmt.getLong(_columnIndexOfStartTimeMillis)
          val _tmpEndTimeMillis: Long
          _tmpEndTimeMillis = _stmt.getLong(_columnIndexOfEndTimeMillis)
          val _tmpSleepDurationMinutes: Int
          _tmpSleepDurationMinutes = _stmt.getLong(_columnIndexOfSleepDurationMinutes).toInt()
          val _tmpTimeInBedMinutes: Int
          _tmpTimeInBedMinutes = _stmt.getLong(_columnIndexOfTimeInBedMinutes).toInt()
          val _tmpSleepEfficiency: Float
          _tmpSleepEfficiency = _stmt.getDouble(_columnIndexOfSleepEfficiency).toFloat()
          val _tmpSleepOnsetMinutes: Int
          _tmpSleepOnsetMinutes = _stmt.getLong(_columnIndexOfSleepOnsetMinutes).toInt()
          val _tmpWakeEvents: Int
          _tmpWakeEvents = _stmt.getLong(_columnIndexOfWakeEvents).toInt()
          val _tmpDeepSleepPercent: Float
          _tmpDeepSleepPercent = _stmt.getDouble(_columnIndexOfDeepSleepPercent).toFloat()
          val _tmpLightSleepPercent: Float
          _tmpLightSleepPercent = _stmt.getDouble(_columnIndexOfLightSleepPercent).toFloat()
          val _tmpRemSleepPercent: Float
          _tmpRemSleepPercent = _stmt.getDouble(_columnIndexOfRemSleepPercent).toFloat()
          val _tmpSleepScore: Int
          _tmpSleepScore = _stmt.getLong(_columnIndexOfSleepScore).toInt()
          val _tmpMoodRating: Int
          _tmpMoodRating = _stmt.getLong(_columnIndexOfMoodRating).toInt()
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpIsCompleted: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCompleted).toInt()
          _tmpIsCompleted = _tmp != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpIsHomeSleep: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsHomeSleep).toInt()
          _tmpIsHomeSleep = _tmp_1 != 0
          val _tmpAlarmUsed: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfAlarmUsed).toInt()
          _tmpAlarmUsed = _tmp_2 != 0
          val _tmpAvgBreathingRateBrpm: Float?
          if (_stmt.isNull(_columnIndexOfAvgBreathingRateBrpm)) {
            _tmpAvgBreathingRateBrpm = null
          } else {
            _tmpAvgBreathingRateBrpm = _stmt.getDouble(_columnIndexOfAvgBreathingRateBrpm).toFloat()
          }
          val _tmpCoughEventCount: Int
          _tmpCoughEventCount = _stmt.getLong(_columnIndexOfCoughEventCount).toInt()
          _item = SleepSessionEntity(_tmpId,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpSleepDurationMinutes,_tmpTimeInBedMinutes,_tmpSleepEfficiency,_tmpSleepOnsetMinutes,_tmpWakeEvents,_tmpDeepSleepPercent,_tmpLightSleepPercent,_tmpRemSleepPercent,_tmpSleepScore,_tmpMoodRating,_tmpNotes,_tmpIsCompleted,_tmpTimezoneId,_tmpIsHomeSleep,_tmpAlarmUsed,_tmpAvgBreathingRateBrpm,_tmpCoughEventCount)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAverageScoreSince(fromMillis: Long): Float? {
    val _sql: String = "SELECT AVG(sleepScore) FROM sleep_sessions WHERE isCompleted = 1 AND startTimeMillis >= ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, fromMillis)
        val _result: Float?
        if (_stmt.step()) {
          val _tmp: Float?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getDouble(0).toFloat()
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getAverageDurationSince(fromMillis: Long): Float? {
    val _sql: String = "SELECT AVG(sleepDurationMinutes) FROM sleep_sessions WHERE isCompleted = 1 AND startTimeMillis >= ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, fromMillis)
        val _result: Float?
        if (_stmt.step()) {
          val _tmp: Float?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getDouble(0).toFloat()
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTotalCompletedCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM sleep_sessions WHERE isCompleted = 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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
