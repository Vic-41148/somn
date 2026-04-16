package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.HabitLogEntity
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
public class HabitLogDao_Impl(
  __db: RoomDatabase,
) : HabitLogDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfHabitLogEntity: EntityInsertAdapter<HabitLogEntity>

  private val __deleteAdapterOfHabitLogEntity: EntityDeleteOrUpdateAdapter<HabitLogEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfHabitLogEntity = object : EntityInsertAdapter<HabitLogEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `habit_logs` (`id`,`date`,`entryType`,`caffeineMg`,`caffeineSource`,`alcoholUnits`,`exerciseType`,`exerciseDurationMinutes`,`exerciseIntensity`,`stressLevel`,`medicationName`,`medicationDose`,`medicationIsStimulant`,`timeOfDayHour`,`timeOfDayMinute`,`notes`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: HabitLogEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.date)
        statement.bindText(3, entity.entryType)
        val _tmpCaffeineMg: Int? = entity.caffeineMg
        if (_tmpCaffeineMg == null) {
          statement.bindNull(4)
        } else {
          statement.bindLong(4, _tmpCaffeineMg.toLong())
        }
        val _tmpCaffeineSource: String? = entity.caffeineSource
        if (_tmpCaffeineSource == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpCaffeineSource)
        }
        val _tmpAlcoholUnits: Float? = entity.alcoholUnits
        if (_tmpAlcoholUnits == null) {
          statement.bindNull(6)
        } else {
          statement.bindDouble(6, _tmpAlcoholUnits.toDouble())
        }
        val _tmpExerciseType: String? = entity.exerciseType
        if (_tmpExerciseType == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpExerciseType)
        }
        val _tmpExerciseDurationMinutes: Int? = entity.exerciseDurationMinutes
        if (_tmpExerciseDurationMinutes == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpExerciseDurationMinutes.toLong())
        }
        val _tmpExerciseIntensity: String? = entity.exerciseIntensity
        if (_tmpExerciseIntensity == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpExerciseIntensity)
        }
        val _tmpStressLevel: Int? = entity.stressLevel
        if (_tmpStressLevel == null) {
          statement.bindNull(10)
        } else {
          statement.bindLong(10, _tmpStressLevel.toLong())
        }
        val _tmpMedicationName: String? = entity.medicationName
        if (_tmpMedicationName == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpMedicationName)
        }
        val _tmpMedicationDose: String? = entity.medicationDose
        if (_tmpMedicationDose == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpMedicationDose)
        }
        val _tmpMedicationIsStimulant: Boolean? = entity.medicationIsStimulant
        val _tmp: Int? = _tmpMedicationIsStimulant?.let { if (it) 1 else 0 }
        if (_tmp == null) {
          statement.bindNull(13)
        } else {
          statement.bindLong(13, _tmp.toLong())
        }
        val _tmpTimeOfDayHour: Int? = entity.timeOfDayHour
        if (_tmpTimeOfDayHour == null) {
          statement.bindNull(14)
        } else {
          statement.bindLong(14, _tmpTimeOfDayHour.toLong())
        }
        val _tmpTimeOfDayMinute: Int? = entity.timeOfDayMinute
        if (_tmpTimeOfDayMinute == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpTimeOfDayMinute.toLong())
        }
        statement.bindText(16, entity.notes)
      }
    }
    this.__deleteAdapterOfHabitLogEntity = object : EntityDeleteOrUpdateAdapter<HabitLogEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `habit_logs` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: HabitLogEntity) {
        statement.bindLong(1, entity.id)
      }
    }
  }

  public override suspend fun insert(log: HabitLogEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfHabitLogEntity.insertAndReturnId(_connection, log)
    _result
  }

  public override suspend fun delete(log: HabitLogEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfHabitLogEntity.handle(_connection, log)
  }

  public override fun getLogsForDate(date: String): Flow<List<HabitLogEntity>> {
    val _sql: String = "SELECT * FROM habit_logs WHERE date = ? ORDER BY id DESC"
    return createFlow(__db, false, arrayOf("habit_logs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfEntryType: Int = getColumnIndexOrThrow(_stmt, "entryType")
        val _columnIndexOfCaffeineMg: Int = getColumnIndexOrThrow(_stmt, "caffeineMg")
        val _columnIndexOfCaffeineSource: Int = getColumnIndexOrThrow(_stmt, "caffeineSource")
        val _columnIndexOfAlcoholUnits: Int = getColumnIndexOrThrow(_stmt, "alcoholUnits")
        val _columnIndexOfExerciseType: Int = getColumnIndexOrThrow(_stmt, "exerciseType")
        val _columnIndexOfExerciseDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "exerciseDurationMinutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exerciseIntensity")
        val _columnIndexOfStressLevel: Int = getColumnIndexOrThrow(_stmt, "stressLevel")
        val _columnIndexOfMedicationName: Int = getColumnIndexOrThrow(_stmt, "medicationName")
        val _columnIndexOfMedicationDose: Int = getColumnIndexOrThrow(_stmt, "medicationDose")
        val _columnIndexOfMedicationIsStimulant: Int = getColumnIndexOrThrow(_stmt, "medicationIsStimulant")
        val _columnIndexOfTimeOfDayHour: Int = getColumnIndexOrThrow(_stmt, "timeOfDayHour")
        val _columnIndexOfTimeOfDayMinute: Int = getColumnIndexOrThrow(_stmt, "timeOfDayMinute")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: MutableList<HabitLogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HabitLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpEntryType: String
          _tmpEntryType = _stmt.getText(_columnIndexOfEntryType)
          val _tmpCaffeineMg: Int?
          if (_stmt.isNull(_columnIndexOfCaffeineMg)) {
            _tmpCaffeineMg = null
          } else {
            _tmpCaffeineMg = _stmt.getLong(_columnIndexOfCaffeineMg).toInt()
          }
          val _tmpCaffeineSource: String?
          if (_stmt.isNull(_columnIndexOfCaffeineSource)) {
            _tmpCaffeineSource = null
          } else {
            _tmpCaffeineSource = _stmt.getText(_columnIndexOfCaffeineSource)
          }
          val _tmpAlcoholUnits: Float?
          if (_stmt.isNull(_columnIndexOfAlcoholUnits)) {
            _tmpAlcoholUnits = null
          } else {
            _tmpAlcoholUnits = _stmt.getDouble(_columnIndexOfAlcoholUnits).toFloat()
          }
          val _tmpExerciseType: String?
          if (_stmt.isNull(_columnIndexOfExerciseType)) {
            _tmpExerciseType = null
          } else {
            _tmpExerciseType = _stmt.getText(_columnIndexOfExerciseType)
          }
          val _tmpExerciseDurationMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseDurationMinutes)) {
            _tmpExerciseDurationMinutes = null
          } else {
            _tmpExerciseDurationMinutes = _stmt.getLong(_columnIndexOfExerciseDurationMinutes).toInt()
          }
          val _tmpExerciseIntensity: String?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = _stmt.getText(_columnIndexOfExerciseIntensity)
          }
          val _tmpStressLevel: Int?
          if (_stmt.isNull(_columnIndexOfStressLevel)) {
            _tmpStressLevel = null
          } else {
            _tmpStressLevel = _stmt.getLong(_columnIndexOfStressLevel).toInt()
          }
          val _tmpMedicationName: String?
          if (_stmt.isNull(_columnIndexOfMedicationName)) {
            _tmpMedicationName = null
          } else {
            _tmpMedicationName = _stmt.getText(_columnIndexOfMedicationName)
          }
          val _tmpMedicationDose: String?
          if (_stmt.isNull(_columnIndexOfMedicationDose)) {
            _tmpMedicationDose = null
          } else {
            _tmpMedicationDose = _stmt.getText(_columnIndexOfMedicationDose)
          }
          val _tmpMedicationIsStimulant: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfMedicationIsStimulant)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfMedicationIsStimulant).toInt()
          }
          _tmpMedicationIsStimulant = _tmp?.let { it != 0 }
          val _tmpTimeOfDayHour: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayHour)) {
            _tmpTimeOfDayHour = null
          } else {
            _tmpTimeOfDayHour = _stmt.getLong(_columnIndexOfTimeOfDayHour).toInt()
          }
          val _tmpTimeOfDayMinute: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayMinute)) {
            _tmpTimeOfDayMinute = null
          } else {
            _tmpTimeOfDayMinute = _stmt.getLong(_columnIndexOfTimeOfDayMinute).toInt()
          }
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          _item = HabitLogEntity(_tmpId,_tmpDate,_tmpEntryType,_tmpCaffeineMg,_tmpCaffeineSource,_tmpAlcoholUnits,_tmpExerciseType,_tmpExerciseDurationMinutes,_tmpExerciseIntensity,_tmpStressLevel,_tmpMedicationName,_tmpMedicationDose,_tmpMedicationIsStimulant,_tmpTimeOfDayHour,_tmpTimeOfDayMinute,_tmpNotes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getLogsInRange(from: String, to: String): Flow<List<HabitLogEntity>> {
    val _sql: String = "SELECT * FROM habit_logs WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC"
    return createFlow(__db, false, arrayOf("habit_logs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, from)
        _argIndex = 2
        _stmt.bindText(_argIndex, to)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfEntryType: Int = getColumnIndexOrThrow(_stmt, "entryType")
        val _columnIndexOfCaffeineMg: Int = getColumnIndexOrThrow(_stmt, "caffeineMg")
        val _columnIndexOfCaffeineSource: Int = getColumnIndexOrThrow(_stmt, "caffeineSource")
        val _columnIndexOfAlcoholUnits: Int = getColumnIndexOrThrow(_stmt, "alcoholUnits")
        val _columnIndexOfExerciseType: Int = getColumnIndexOrThrow(_stmt, "exerciseType")
        val _columnIndexOfExerciseDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "exerciseDurationMinutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exerciseIntensity")
        val _columnIndexOfStressLevel: Int = getColumnIndexOrThrow(_stmt, "stressLevel")
        val _columnIndexOfMedicationName: Int = getColumnIndexOrThrow(_stmt, "medicationName")
        val _columnIndexOfMedicationDose: Int = getColumnIndexOrThrow(_stmt, "medicationDose")
        val _columnIndexOfMedicationIsStimulant: Int = getColumnIndexOrThrow(_stmt, "medicationIsStimulant")
        val _columnIndexOfTimeOfDayHour: Int = getColumnIndexOrThrow(_stmt, "timeOfDayHour")
        val _columnIndexOfTimeOfDayMinute: Int = getColumnIndexOrThrow(_stmt, "timeOfDayMinute")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: MutableList<HabitLogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HabitLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpEntryType: String
          _tmpEntryType = _stmt.getText(_columnIndexOfEntryType)
          val _tmpCaffeineMg: Int?
          if (_stmt.isNull(_columnIndexOfCaffeineMg)) {
            _tmpCaffeineMg = null
          } else {
            _tmpCaffeineMg = _stmt.getLong(_columnIndexOfCaffeineMg).toInt()
          }
          val _tmpCaffeineSource: String?
          if (_stmt.isNull(_columnIndexOfCaffeineSource)) {
            _tmpCaffeineSource = null
          } else {
            _tmpCaffeineSource = _stmt.getText(_columnIndexOfCaffeineSource)
          }
          val _tmpAlcoholUnits: Float?
          if (_stmt.isNull(_columnIndexOfAlcoholUnits)) {
            _tmpAlcoholUnits = null
          } else {
            _tmpAlcoholUnits = _stmt.getDouble(_columnIndexOfAlcoholUnits).toFloat()
          }
          val _tmpExerciseType: String?
          if (_stmt.isNull(_columnIndexOfExerciseType)) {
            _tmpExerciseType = null
          } else {
            _tmpExerciseType = _stmt.getText(_columnIndexOfExerciseType)
          }
          val _tmpExerciseDurationMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseDurationMinutes)) {
            _tmpExerciseDurationMinutes = null
          } else {
            _tmpExerciseDurationMinutes = _stmt.getLong(_columnIndexOfExerciseDurationMinutes).toInt()
          }
          val _tmpExerciseIntensity: String?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = _stmt.getText(_columnIndexOfExerciseIntensity)
          }
          val _tmpStressLevel: Int?
          if (_stmt.isNull(_columnIndexOfStressLevel)) {
            _tmpStressLevel = null
          } else {
            _tmpStressLevel = _stmt.getLong(_columnIndexOfStressLevel).toInt()
          }
          val _tmpMedicationName: String?
          if (_stmt.isNull(_columnIndexOfMedicationName)) {
            _tmpMedicationName = null
          } else {
            _tmpMedicationName = _stmt.getText(_columnIndexOfMedicationName)
          }
          val _tmpMedicationDose: String?
          if (_stmt.isNull(_columnIndexOfMedicationDose)) {
            _tmpMedicationDose = null
          } else {
            _tmpMedicationDose = _stmt.getText(_columnIndexOfMedicationDose)
          }
          val _tmpMedicationIsStimulant: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfMedicationIsStimulant)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfMedicationIsStimulant).toInt()
          }
          _tmpMedicationIsStimulant = _tmp?.let { it != 0 }
          val _tmpTimeOfDayHour: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayHour)) {
            _tmpTimeOfDayHour = null
          } else {
            _tmpTimeOfDayHour = _stmt.getLong(_columnIndexOfTimeOfDayHour).toInt()
          }
          val _tmpTimeOfDayMinute: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayMinute)) {
            _tmpTimeOfDayMinute = null
          } else {
            _tmpTimeOfDayMinute = _stmt.getLong(_columnIndexOfTimeOfDayMinute).toInt()
          }
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          _item = HabitLogEntity(_tmpId,_tmpDate,_tmpEntryType,_tmpCaffeineMg,_tmpCaffeineSource,_tmpAlcoholUnits,_tmpExerciseType,_tmpExerciseDurationMinutes,_tmpExerciseIntensity,_tmpStressLevel,_tmpMedicationName,_tmpMedicationDose,_tmpMedicationIsStimulant,_tmpTimeOfDayHour,_tmpTimeOfDayMinute,_tmpNotes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getLogsByType(type: String): Flow<List<HabitLogEntity>> {
    val _sql: String = "SELECT * FROM habit_logs WHERE entryType = ? ORDER BY date DESC"
    return createFlow(__db, false, arrayOf("habit_logs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, type)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfEntryType: Int = getColumnIndexOrThrow(_stmt, "entryType")
        val _columnIndexOfCaffeineMg: Int = getColumnIndexOrThrow(_stmt, "caffeineMg")
        val _columnIndexOfCaffeineSource: Int = getColumnIndexOrThrow(_stmt, "caffeineSource")
        val _columnIndexOfAlcoholUnits: Int = getColumnIndexOrThrow(_stmt, "alcoholUnits")
        val _columnIndexOfExerciseType: Int = getColumnIndexOrThrow(_stmt, "exerciseType")
        val _columnIndexOfExerciseDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "exerciseDurationMinutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exerciseIntensity")
        val _columnIndexOfStressLevel: Int = getColumnIndexOrThrow(_stmt, "stressLevel")
        val _columnIndexOfMedicationName: Int = getColumnIndexOrThrow(_stmt, "medicationName")
        val _columnIndexOfMedicationDose: Int = getColumnIndexOrThrow(_stmt, "medicationDose")
        val _columnIndexOfMedicationIsStimulant: Int = getColumnIndexOrThrow(_stmt, "medicationIsStimulant")
        val _columnIndexOfTimeOfDayHour: Int = getColumnIndexOrThrow(_stmt, "timeOfDayHour")
        val _columnIndexOfTimeOfDayMinute: Int = getColumnIndexOrThrow(_stmt, "timeOfDayMinute")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: MutableList<HabitLogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HabitLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpEntryType: String
          _tmpEntryType = _stmt.getText(_columnIndexOfEntryType)
          val _tmpCaffeineMg: Int?
          if (_stmt.isNull(_columnIndexOfCaffeineMg)) {
            _tmpCaffeineMg = null
          } else {
            _tmpCaffeineMg = _stmt.getLong(_columnIndexOfCaffeineMg).toInt()
          }
          val _tmpCaffeineSource: String?
          if (_stmt.isNull(_columnIndexOfCaffeineSource)) {
            _tmpCaffeineSource = null
          } else {
            _tmpCaffeineSource = _stmt.getText(_columnIndexOfCaffeineSource)
          }
          val _tmpAlcoholUnits: Float?
          if (_stmt.isNull(_columnIndexOfAlcoholUnits)) {
            _tmpAlcoholUnits = null
          } else {
            _tmpAlcoholUnits = _stmt.getDouble(_columnIndexOfAlcoholUnits).toFloat()
          }
          val _tmpExerciseType: String?
          if (_stmt.isNull(_columnIndexOfExerciseType)) {
            _tmpExerciseType = null
          } else {
            _tmpExerciseType = _stmt.getText(_columnIndexOfExerciseType)
          }
          val _tmpExerciseDurationMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseDurationMinutes)) {
            _tmpExerciseDurationMinutes = null
          } else {
            _tmpExerciseDurationMinutes = _stmt.getLong(_columnIndexOfExerciseDurationMinutes).toInt()
          }
          val _tmpExerciseIntensity: String?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = _stmt.getText(_columnIndexOfExerciseIntensity)
          }
          val _tmpStressLevel: Int?
          if (_stmt.isNull(_columnIndexOfStressLevel)) {
            _tmpStressLevel = null
          } else {
            _tmpStressLevel = _stmt.getLong(_columnIndexOfStressLevel).toInt()
          }
          val _tmpMedicationName: String?
          if (_stmt.isNull(_columnIndexOfMedicationName)) {
            _tmpMedicationName = null
          } else {
            _tmpMedicationName = _stmt.getText(_columnIndexOfMedicationName)
          }
          val _tmpMedicationDose: String?
          if (_stmt.isNull(_columnIndexOfMedicationDose)) {
            _tmpMedicationDose = null
          } else {
            _tmpMedicationDose = _stmt.getText(_columnIndexOfMedicationDose)
          }
          val _tmpMedicationIsStimulant: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfMedicationIsStimulant)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfMedicationIsStimulant).toInt()
          }
          _tmpMedicationIsStimulant = _tmp?.let { it != 0 }
          val _tmpTimeOfDayHour: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayHour)) {
            _tmpTimeOfDayHour = null
          } else {
            _tmpTimeOfDayHour = _stmt.getLong(_columnIndexOfTimeOfDayHour).toInt()
          }
          val _tmpTimeOfDayMinute: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayMinute)) {
            _tmpTimeOfDayMinute = null
          } else {
            _tmpTimeOfDayMinute = _stmt.getLong(_columnIndexOfTimeOfDayMinute).toInt()
          }
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          _item = HabitLogEntity(_tmpId,_tmpDate,_tmpEntryType,_tmpCaffeineMg,_tmpCaffeineSource,_tmpAlcoholUnits,_tmpExerciseType,_tmpExerciseDurationMinutes,_tmpExerciseIntensity,_tmpStressLevel,_tmpMedicationName,_tmpMedicationDose,_tmpMedicationIsStimulant,_tmpTimeOfDayHour,_tmpTimeOfDayMinute,_tmpNotes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRecentLogsByType(type: String, limit: Int): Flow<List<HabitLogEntity>> {
    val _sql: String = "SELECT * FROM habit_logs WHERE entryType = ? ORDER BY date DESC, id DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("habit_logs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, type)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfEntryType: Int = getColumnIndexOrThrow(_stmt, "entryType")
        val _columnIndexOfCaffeineMg: Int = getColumnIndexOrThrow(_stmt, "caffeineMg")
        val _columnIndexOfCaffeineSource: Int = getColumnIndexOrThrow(_stmt, "caffeineSource")
        val _columnIndexOfAlcoholUnits: Int = getColumnIndexOrThrow(_stmt, "alcoholUnits")
        val _columnIndexOfExerciseType: Int = getColumnIndexOrThrow(_stmt, "exerciseType")
        val _columnIndexOfExerciseDurationMinutes: Int = getColumnIndexOrThrow(_stmt, "exerciseDurationMinutes")
        val _columnIndexOfExerciseIntensity: Int = getColumnIndexOrThrow(_stmt, "exerciseIntensity")
        val _columnIndexOfStressLevel: Int = getColumnIndexOrThrow(_stmt, "stressLevel")
        val _columnIndexOfMedicationName: Int = getColumnIndexOrThrow(_stmt, "medicationName")
        val _columnIndexOfMedicationDose: Int = getColumnIndexOrThrow(_stmt, "medicationDose")
        val _columnIndexOfMedicationIsStimulant: Int = getColumnIndexOrThrow(_stmt, "medicationIsStimulant")
        val _columnIndexOfTimeOfDayHour: Int = getColumnIndexOrThrow(_stmt, "timeOfDayHour")
        val _columnIndexOfTimeOfDayMinute: Int = getColumnIndexOrThrow(_stmt, "timeOfDayMinute")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _result: MutableList<HabitLogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HabitLogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDate: String
          _tmpDate = _stmt.getText(_columnIndexOfDate)
          val _tmpEntryType: String
          _tmpEntryType = _stmt.getText(_columnIndexOfEntryType)
          val _tmpCaffeineMg: Int?
          if (_stmt.isNull(_columnIndexOfCaffeineMg)) {
            _tmpCaffeineMg = null
          } else {
            _tmpCaffeineMg = _stmt.getLong(_columnIndexOfCaffeineMg).toInt()
          }
          val _tmpCaffeineSource: String?
          if (_stmt.isNull(_columnIndexOfCaffeineSource)) {
            _tmpCaffeineSource = null
          } else {
            _tmpCaffeineSource = _stmt.getText(_columnIndexOfCaffeineSource)
          }
          val _tmpAlcoholUnits: Float?
          if (_stmt.isNull(_columnIndexOfAlcoholUnits)) {
            _tmpAlcoholUnits = null
          } else {
            _tmpAlcoholUnits = _stmt.getDouble(_columnIndexOfAlcoholUnits).toFloat()
          }
          val _tmpExerciseType: String?
          if (_stmt.isNull(_columnIndexOfExerciseType)) {
            _tmpExerciseType = null
          } else {
            _tmpExerciseType = _stmt.getText(_columnIndexOfExerciseType)
          }
          val _tmpExerciseDurationMinutes: Int?
          if (_stmt.isNull(_columnIndexOfExerciseDurationMinutes)) {
            _tmpExerciseDurationMinutes = null
          } else {
            _tmpExerciseDurationMinutes = _stmt.getLong(_columnIndexOfExerciseDurationMinutes).toInt()
          }
          val _tmpExerciseIntensity: String?
          if (_stmt.isNull(_columnIndexOfExerciseIntensity)) {
            _tmpExerciseIntensity = null
          } else {
            _tmpExerciseIntensity = _stmt.getText(_columnIndexOfExerciseIntensity)
          }
          val _tmpStressLevel: Int?
          if (_stmt.isNull(_columnIndexOfStressLevel)) {
            _tmpStressLevel = null
          } else {
            _tmpStressLevel = _stmt.getLong(_columnIndexOfStressLevel).toInt()
          }
          val _tmpMedicationName: String?
          if (_stmt.isNull(_columnIndexOfMedicationName)) {
            _tmpMedicationName = null
          } else {
            _tmpMedicationName = _stmt.getText(_columnIndexOfMedicationName)
          }
          val _tmpMedicationDose: String?
          if (_stmt.isNull(_columnIndexOfMedicationDose)) {
            _tmpMedicationDose = null
          } else {
            _tmpMedicationDose = _stmt.getText(_columnIndexOfMedicationDose)
          }
          val _tmpMedicationIsStimulant: Boolean?
          val _tmp: Int?
          if (_stmt.isNull(_columnIndexOfMedicationIsStimulant)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(_columnIndexOfMedicationIsStimulant).toInt()
          }
          _tmpMedicationIsStimulant = _tmp?.let { it != 0 }
          val _tmpTimeOfDayHour: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayHour)) {
            _tmpTimeOfDayHour = null
          } else {
            _tmpTimeOfDayHour = _stmt.getLong(_columnIndexOfTimeOfDayHour).toInt()
          }
          val _tmpTimeOfDayMinute: Int?
          if (_stmt.isNull(_columnIndexOfTimeOfDayMinute)) {
            _tmpTimeOfDayMinute = null
          } else {
            _tmpTimeOfDayMinute = _stmt.getLong(_columnIndexOfTimeOfDayMinute).toInt()
          }
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          _item = HabitLogEntity(_tmpId,_tmpDate,_tmpEntryType,_tmpCaffeineMg,_tmpCaffeineSource,_tmpAlcoholUnits,_tmpExerciseType,_tmpExerciseDurationMinutes,_tmpExerciseIntensity,_tmpStressLevel,_tmpMedicationName,_tmpMedicationDose,_tmpMedicationIsStimulant,_tmpTimeOfDayHour,_tmpTimeOfDayMinute,_tmpNotes)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM habit_logs WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
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
