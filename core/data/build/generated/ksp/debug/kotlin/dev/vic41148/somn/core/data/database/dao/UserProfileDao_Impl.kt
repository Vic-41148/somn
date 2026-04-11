package dev.vic41148.somn.core.`data`.database.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import dev.vic41148.somn.core.`data`.database.entity.UserProfileEntity
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class UserProfileDao_Impl(
  __db: RoomDatabase,
) : UserProfileDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfUserProfileEntity: EntityInsertAdapter<UserProfileEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfUserProfileEntity = object : EntityInsertAdapter<UserProfileEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `user_profile` (`id`,`dateOfBirth`,`biologicalSex`,`lifeStage`,`chronotype`,`chronotypeMeqScore`,`adhdMode`,`asdMode`,`medicationTracking`,`targetSleepHours`,`pregnancyTrimester`,`pregnancyDueDate`,`cycleLength`,`lastPeriodStartDate`,`shiftWorker`,`timezoneId`,`onboardingCompleted`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: UserProfileEntity) {
        statement.bindLong(1, entity.id)
        val _tmpDateOfBirth: String? = entity.dateOfBirth
        if (_tmpDateOfBirth == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpDateOfBirth)
        }
        statement.bindText(3, entity.biologicalSex)
        statement.bindText(4, entity.lifeStage)
        statement.bindText(5, entity.chronotype)
        val _tmpChronotypeMeqScore: Int? = entity.chronotypeMeqScore
        if (_tmpChronotypeMeqScore == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpChronotypeMeqScore.toLong())
        }
        val _tmp: Int = if (entity.adhdMode) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmp_1: Int = if (entity.asdMode) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.medicationTracking) 1 else 0
        statement.bindLong(9, _tmp_2.toLong())
        statement.bindDouble(10, entity.targetSleepHours.toDouble())
        val _tmpPregnancyTrimester: Int? = entity.pregnancyTrimester
        if (_tmpPregnancyTrimester == null) {
          statement.bindNull(11)
        } else {
          statement.bindLong(11, _tmpPregnancyTrimester.toLong())
        }
        val _tmpPregnancyDueDate: String? = entity.pregnancyDueDate
        if (_tmpPregnancyDueDate == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpPregnancyDueDate)
        }
        statement.bindLong(13, entity.cycleLength.toLong())
        val _tmpLastPeriodStartDate: String? = entity.lastPeriodStartDate
        if (_tmpLastPeriodStartDate == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpLastPeriodStartDate)
        }
        val _tmp_3: Int = if (entity.shiftWorker) 1 else 0
        statement.bindLong(15, _tmp_3.toLong())
        statement.bindText(16, entity.timezoneId)
        val _tmp_4: Int = if (entity.onboardingCompleted) 1 else 0
        statement.bindLong(17, _tmp_4.toLong())
      }
    }
  }

  public override suspend fun upsert(profile: UserProfileEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfUserProfileEntity.insert(_connection, profile)
  }

  public override suspend fun getProfile(): UserProfileEntity? {
    val _sql: String = "SELECT * FROM user_profile WHERE id = 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDateOfBirth: Int = getColumnIndexOrThrow(_stmt, "dateOfBirth")
        val _columnIndexOfBiologicalSex: Int = getColumnIndexOrThrow(_stmt, "biologicalSex")
        val _columnIndexOfLifeStage: Int = getColumnIndexOrThrow(_stmt, "lifeStage")
        val _columnIndexOfChronotype: Int = getColumnIndexOrThrow(_stmt, "chronotype")
        val _columnIndexOfChronotypeMeqScore: Int = getColumnIndexOrThrow(_stmt, "chronotypeMeqScore")
        val _columnIndexOfAdhdMode: Int = getColumnIndexOrThrow(_stmt, "adhdMode")
        val _columnIndexOfAsdMode: Int = getColumnIndexOrThrow(_stmt, "asdMode")
        val _columnIndexOfMedicationTracking: Int = getColumnIndexOrThrow(_stmt, "medicationTracking")
        val _columnIndexOfTargetSleepHours: Int = getColumnIndexOrThrow(_stmt, "targetSleepHours")
        val _columnIndexOfPregnancyTrimester: Int = getColumnIndexOrThrow(_stmt, "pregnancyTrimester")
        val _columnIndexOfPregnancyDueDate: Int = getColumnIndexOrThrow(_stmt, "pregnancyDueDate")
        val _columnIndexOfCycleLength: Int = getColumnIndexOrThrow(_stmt, "cycleLength")
        val _columnIndexOfLastPeriodStartDate: Int = getColumnIndexOrThrow(_stmt, "lastPeriodStartDate")
        val _columnIndexOfShiftWorker: Int = getColumnIndexOrThrow(_stmt, "shiftWorker")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfOnboardingCompleted: Int = getColumnIndexOrThrow(_stmt, "onboardingCompleted")
        val _result: UserProfileEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDateOfBirth: String?
          if (_stmt.isNull(_columnIndexOfDateOfBirth)) {
            _tmpDateOfBirth = null
          } else {
            _tmpDateOfBirth = _stmt.getText(_columnIndexOfDateOfBirth)
          }
          val _tmpBiologicalSex: String
          _tmpBiologicalSex = _stmt.getText(_columnIndexOfBiologicalSex)
          val _tmpLifeStage: String
          _tmpLifeStage = _stmt.getText(_columnIndexOfLifeStage)
          val _tmpChronotype: String
          _tmpChronotype = _stmt.getText(_columnIndexOfChronotype)
          val _tmpChronotypeMeqScore: Int?
          if (_stmt.isNull(_columnIndexOfChronotypeMeqScore)) {
            _tmpChronotypeMeqScore = null
          } else {
            _tmpChronotypeMeqScore = _stmt.getLong(_columnIndexOfChronotypeMeqScore).toInt()
          }
          val _tmpAdhdMode: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfAdhdMode).toInt()
          _tmpAdhdMode = _tmp != 0
          val _tmpAsdMode: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfAsdMode).toInt()
          _tmpAsdMode = _tmp_1 != 0
          val _tmpMedicationTracking: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfMedicationTracking).toInt()
          _tmpMedicationTracking = _tmp_2 != 0
          val _tmpTargetSleepHours: Float
          _tmpTargetSleepHours = _stmt.getDouble(_columnIndexOfTargetSleepHours).toFloat()
          val _tmpPregnancyTrimester: Int?
          if (_stmt.isNull(_columnIndexOfPregnancyTrimester)) {
            _tmpPregnancyTrimester = null
          } else {
            _tmpPregnancyTrimester = _stmt.getLong(_columnIndexOfPregnancyTrimester).toInt()
          }
          val _tmpPregnancyDueDate: String?
          if (_stmt.isNull(_columnIndexOfPregnancyDueDate)) {
            _tmpPregnancyDueDate = null
          } else {
            _tmpPregnancyDueDate = _stmt.getText(_columnIndexOfPregnancyDueDate)
          }
          val _tmpCycleLength: Int
          _tmpCycleLength = _stmt.getLong(_columnIndexOfCycleLength).toInt()
          val _tmpLastPeriodStartDate: String?
          if (_stmt.isNull(_columnIndexOfLastPeriodStartDate)) {
            _tmpLastPeriodStartDate = null
          } else {
            _tmpLastPeriodStartDate = _stmt.getText(_columnIndexOfLastPeriodStartDate)
          }
          val _tmpShiftWorker: Boolean
          val _tmp_3: Int
          _tmp_3 = _stmt.getLong(_columnIndexOfShiftWorker).toInt()
          _tmpShiftWorker = _tmp_3 != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpOnboardingCompleted: Boolean
          val _tmp_4: Int
          _tmp_4 = _stmt.getLong(_columnIndexOfOnboardingCompleted).toInt()
          _tmpOnboardingCompleted = _tmp_4 != 0
          _result = UserProfileEntity(_tmpId,_tmpDateOfBirth,_tmpBiologicalSex,_tmpLifeStage,_tmpChronotype,_tmpChronotypeMeqScore,_tmpAdhdMode,_tmpAsdMode,_tmpMedicationTracking,_tmpTargetSleepHours,_tmpPregnancyTrimester,_tmpPregnancyDueDate,_tmpCycleLength,_tmpLastPeriodStartDate,_tmpShiftWorker,_tmpTimezoneId,_tmpOnboardingCompleted)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeProfile(): Flow<UserProfileEntity?> {
    val _sql: String = "SELECT * FROM user_profile WHERE id = 1"
    return createFlow(__db, false, arrayOf("user_profile")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDateOfBirth: Int = getColumnIndexOrThrow(_stmt, "dateOfBirth")
        val _columnIndexOfBiologicalSex: Int = getColumnIndexOrThrow(_stmt, "biologicalSex")
        val _columnIndexOfLifeStage: Int = getColumnIndexOrThrow(_stmt, "lifeStage")
        val _columnIndexOfChronotype: Int = getColumnIndexOrThrow(_stmt, "chronotype")
        val _columnIndexOfChronotypeMeqScore: Int = getColumnIndexOrThrow(_stmt, "chronotypeMeqScore")
        val _columnIndexOfAdhdMode: Int = getColumnIndexOrThrow(_stmt, "adhdMode")
        val _columnIndexOfAsdMode: Int = getColumnIndexOrThrow(_stmt, "asdMode")
        val _columnIndexOfMedicationTracking: Int = getColumnIndexOrThrow(_stmt, "medicationTracking")
        val _columnIndexOfTargetSleepHours: Int = getColumnIndexOrThrow(_stmt, "targetSleepHours")
        val _columnIndexOfPregnancyTrimester: Int = getColumnIndexOrThrow(_stmt, "pregnancyTrimester")
        val _columnIndexOfPregnancyDueDate: Int = getColumnIndexOrThrow(_stmt, "pregnancyDueDate")
        val _columnIndexOfCycleLength: Int = getColumnIndexOrThrow(_stmt, "cycleLength")
        val _columnIndexOfLastPeriodStartDate: Int = getColumnIndexOrThrow(_stmt, "lastPeriodStartDate")
        val _columnIndexOfShiftWorker: Int = getColumnIndexOrThrow(_stmt, "shiftWorker")
        val _columnIndexOfTimezoneId: Int = getColumnIndexOrThrow(_stmt, "timezoneId")
        val _columnIndexOfOnboardingCompleted: Int = getColumnIndexOrThrow(_stmt, "onboardingCompleted")
        val _result: UserProfileEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDateOfBirth: String?
          if (_stmt.isNull(_columnIndexOfDateOfBirth)) {
            _tmpDateOfBirth = null
          } else {
            _tmpDateOfBirth = _stmt.getText(_columnIndexOfDateOfBirth)
          }
          val _tmpBiologicalSex: String
          _tmpBiologicalSex = _stmt.getText(_columnIndexOfBiologicalSex)
          val _tmpLifeStage: String
          _tmpLifeStage = _stmt.getText(_columnIndexOfLifeStage)
          val _tmpChronotype: String
          _tmpChronotype = _stmt.getText(_columnIndexOfChronotype)
          val _tmpChronotypeMeqScore: Int?
          if (_stmt.isNull(_columnIndexOfChronotypeMeqScore)) {
            _tmpChronotypeMeqScore = null
          } else {
            _tmpChronotypeMeqScore = _stmt.getLong(_columnIndexOfChronotypeMeqScore).toInt()
          }
          val _tmpAdhdMode: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfAdhdMode).toInt()
          _tmpAdhdMode = _tmp != 0
          val _tmpAsdMode: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfAsdMode).toInt()
          _tmpAsdMode = _tmp_1 != 0
          val _tmpMedicationTracking: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfMedicationTracking).toInt()
          _tmpMedicationTracking = _tmp_2 != 0
          val _tmpTargetSleepHours: Float
          _tmpTargetSleepHours = _stmt.getDouble(_columnIndexOfTargetSleepHours).toFloat()
          val _tmpPregnancyTrimester: Int?
          if (_stmt.isNull(_columnIndexOfPregnancyTrimester)) {
            _tmpPregnancyTrimester = null
          } else {
            _tmpPregnancyTrimester = _stmt.getLong(_columnIndexOfPregnancyTrimester).toInt()
          }
          val _tmpPregnancyDueDate: String?
          if (_stmt.isNull(_columnIndexOfPregnancyDueDate)) {
            _tmpPregnancyDueDate = null
          } else {
            _tmpPregnancyDueDate = _stmt.getText(_columnIndexOfPregnancyDueDate)
          }
          val _tmpCycleLength: Int
          _tmpCycleLength = _stmt.getLong(_columnIndexOfCycleLength).toInt()
          val _tmpLastPeriodStartDate: String?
          if (_stmt.isNull(_columnIndexOfLastPeriodStartDate)) {
            _tmpLastPeriodStartDate = null
          } else {
            _tmpLastPeriodStartDate = _stmt.getText(_columnIndexOfLastPeriodStartDate)
          }
          val _tmpShiftWorker: Boolean
          val _tmp_3: Int
          _tmp_3 = _stmt.getLong(_columnIndexOfShiftWorker).toInt()
          _tmpShiftWorker = _tmp_3 != 0
          val _tmpTimezoneId: String
          _tmpTimezoneId = _stmt.getText(_columnIndexOfTimezoneId)
          val _tmpOnboardingCompleted: Boolean
          val _tmp_4: Int
          _tmp_4 = _stmt.getLong(_columnIndexOfOnboardingCompleted).toInt()
          _tmpOnboardingCompleted = _tmp_4 != 0
          _result = UserProfileEntity(_tmpId,_tmpDateOfBirth,_tmpBiologicalSex,_tmpLifeStage,_tmpChronotype,_tmpChronotypeMeqScore,_tmpAdhdMode,_tmpAsdMode,_tmpMedicationTracking,_tmpTargetSleepHours,_tmpPregnancyTrimester,_tmpPregnancyDueDate,_tmpCycleLength,_tmpLastPeriodStartDate,_tmpShiftWorker,_tmpTimezoneId,_tmpOnboardingCompleted)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun isOnboardingCompleted(): Boolean? {
    val _sql: String = "SELECT onboardingCompleted FROM user_profile WHERE id = 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Boolean?
        if (_stmt.step()) {
          val _tmp: Int?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0).toInt()
          }
          _result = _tmp?.let { it != 0 }
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeOnboardingCompleted(): Flow<Boolean?> {
    val _sql: String = "SELECT onboardingCompleted FROM user_profile WHERE id = 1"
    return createFlow(__db, false, arrayOf("user_profile")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Boolean?
        if (_stmt.step()) {
          val _tmp: Int?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0).toInt()
          }
          _result = _tmp?.let { it != 0 }
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markOnboardingCompleted() {
    val _sql: String = "UPDATE user_profile SET onboardingCompleted = 1 WHERE id = 1"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateLastPeriodStart(date: String) {
    val _sql: String = "UPDATE user_profile SET lastPeriodStartDate = ? WHERE id = 1"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, date)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updatePregnancyTrimester(trimester: Int) {
    val _sql: String = "UPDATE user_profile SET pregnancyTrimester = ? WHERE id = 1"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, trimester.toLong())
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
