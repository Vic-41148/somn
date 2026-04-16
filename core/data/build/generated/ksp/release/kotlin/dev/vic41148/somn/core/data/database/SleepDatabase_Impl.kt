package dev.vic41148.somn.core.`data`.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.vic41148.somn.core.`data`.database.dao.AlarmDao
import dev.vic41148.somn.core.`data`.database.dao.AlarmDao_Impl
import dev.vic41148.somn.core.`data`.database.dao.AudioEventDao
import dev.vic41148.somn.core.`data`.database.dao.AudioEventDao_Impl
import dev.vic41148.somn.core.`data`.database.dao.HabitLogDao
import dev.vic41148.somn.core.`data`.database.dao.HabitLogDao_Impl
import dev.vic41148.somn.core.`data`.database.dao.SleepEpochDao
import dev.vic41148.somn.core.`data`.database.dao.SleepEpochDao_Impl
import dev.vic41148.somn.core.`data`.database.dao.SleepSessionDao
import dev.vic41148.somn.core.`data`.database.dao.SleepSessionDao_Impl
import dev.vic41148.somn.core.`data`.database.dao.TagDao
import dev.vic41148.somn.core.`data`.database.dao.TagDao_Impl
import dev.vic41148.somn.core.`data`.database.dao.UserProfileDao
import dev.vic41148.somn.core.`data`.database.dao.UserProfileDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SleepDatabase_Impl : SleepDatabase() {
  private val _sleepSessionDao: Lazy<SleepSessionDao> = lazy {
    SleepSessionDao_Impl(this)
  }

  private val _sleepEpochDao: Lazy<SleepEpochDao> = lazy {
    SleepEpochDao_Impl(this)
  }

  private val _alarmDao: Lazy<AlarmDao> = lazy {
    AlarmDao_Impl(this)
  }

  private val _tagDao: Lazy<TagDao> = lazy {
    TagDao_Impl(this)
  }

  private val _userProfileDao: Lazy<UserProfileDao> = lazy {
    UserProfileDao_Impl(this)
  }

  private val _habitLogDao: Lazy<HabitLogDao> = lazy {
    HabitLogDao_Impl(this)
  }

  private val _audioEventDao: Lazy<AudioEventDao> = lazy {
    AudioEventDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(6, "45aab70825806de7e3833e92f2127527", "4f1052d858ab748b7808f9439ecd49d6") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sleep_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER NOT NULL, `sleepDurationMinutes` INTEGER NOT NULL, `timeInBedMinutes` INTEGER NOT NULL, `sleepEfficiency` REAL NOT NULL, `sleepOnsetMinutes` INTEGER NOT NULL, `wakeEvents` INTEGER NOT NULL, `deepSleepPercent` REAL NOT NULL, `lightSleepPercent` REAL NOT NULL, `remSleepPercent` REAL NOT NULL, `sleepScore` INTEGER NOT NULL, `moodRating` INTEGER NOT NULL, `notes` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `timezoneId` TEXT NOT NULL, `isHomeSleep` INTEGER NOT NULL, `alarmUsed` INTEGER NOT NULL, `avgBreathingRateBrpm` REAL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sleep_epochs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, `stage` TEXT NOT NULL, `movementMagnitude` REAL NOT NULL, `movementVariability` REAL NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_epochs_sessionId` ON `sleep_epochs` (`sessionId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `label` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `repeatDays` TEXT NOT NULL, `wakeWindowMinutes` INTEGER NOT NULL, `snoozeDurationMinutes` INTEGER NOT NULL, `maxSnoozeCount` INTEGER NOT NULL, `soundUri` TEXT NOT NULL, `vibrationEnabled` INTEGER NOT NULL, `gradualVolumeSeconds` INTEGER NOT NULL, `captchaType` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `color` INTEGER NOT NULL, `icon` TEXT NOT NULL, `isArchived` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `session_tags` (`sessionId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `tagId`), FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_session_tags_sessionId` ON `session_tags` (`sessionId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_session_tags_tagId` ON `session_tags` (`tagId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `dateOfBirth` TEXT, `biologicalSex` TEXT NOT NULL, `lifeStage` TEXT NOT NULL, `chronotype` TEXT NOT NULL, `chronotypeMeqScore` INTEGER, `adhdMode` INTEGER NOT NULL, `asdMode` INTEGER NOT NULL, `medicationTracking` INTEGER NOT NULL, `targetSleepHours` REAL NOT NULL, `pregnancyTrimester` INTEGER, `pregnancyDueDate` TEXT, `cycleLength` INTEGER NOT NULL, `lastPeriodStartDate` TEXT, `shiftWorker` INTEGER NOT NULL, `timezoneId` TEXT NOT NULL, `onboardingCompleted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `habit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `entryType` TEXT NOT NULL, `caffeineMg` INTEGER, `caffeineSource` TEXT, `alcoholUnits` REAL, `exerciseType` TEXT, `exerciseDurationMinutes` INTEGER, `exerciseIntensity` TEXT, `stressLevel` INTEGER, `medicationName` TEXT, `medicationDose` TEXT, `medicationIsStimulant` INTEGER, `timeOfDayHour` INTEGER, `timeOfDayMinute` INTEGER, `notes` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `audio_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `type` TEXT NOT NULL, `intensityDecibels` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '45aab70825806de7e3833e92f2127527')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `sleep_sessions`")
        connection.execSQL("DROP TABLE IF EXISTS `sleep_epochs`")
        connection.execSQL("DROP TABLE IF EXISTS `alarms`")
        connection.execSQL("DROP TABLE IF EXISTS `tags`")
        connection.execSQL("DROP TABLE IF EXISTS `session_tags`")
        connection.execSQL("DROP TABLE IF EXISTS `user_profile`")
        connection.execSQL("DROP TABLE IF EXISTS `habit_logs`")
        connection.execSQL("DROP TABLE IF EXISTS `audio_events`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsSleepSessions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSleepSessions.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("startTimeMillis", TableInfo.Column("startTimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("endTimeMillis", TableInfo.Column("endTimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("sleepDurationMinutes", TableInfo.Column("sleepDurationMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("timeInBedMinutes", TableInfo.Column("timeInBedMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("sleepEfficiency", TableInfo.Column("sleepEfficiency", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("sleepOnsetMinutes", TableInfo.Column("sleepOnsetMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("wakeEvents", TableInfo.Column("wakeEvents", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("deepSleepPercent", TableInfo.Column("deepSleepPercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("lightSleepPercent", TableInfo.Column("lightSleepPercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("remSleepPercent", TableInfo.Column("remSleepPercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("sleepScore", TableInfo.Column("sleepScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("moodRating", TableInfo.Column("moodRating", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("notes", TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("isCompleted", TableInfo.Column("isCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("timezoneId", TableInfo.Column("timezoneId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("isHomeSleep", TableInfo.Column("isHomeSleep", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("alarmUsed", TableInfo.Column("alarmUsed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepSessions.put("avgBreathingRateBrpm", TableInfo.Column("avgBreathingRateBrpm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSleepSessions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSleepSessions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSleepSessions: TableInfo = TableInfo("sleep_sessions", _columnsSleepSessions, _foreignKeysSleepSessions, _indicesSleepSessions)
        val _existingSleepSessions: TableInfo = read(connection, "sleep_sessions")
        if (!_infoSleepSessions.equals(_existingSleepSessions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |sleep_sessions(dev.vic41148.somn.core.data.database.entity.SleepSessionEntity).
              | Expected:
              |""".trimMargin() + _infoSleepSessions + """
              |
              | Found:
              |""".trimMargin() + _existingSleepSessions)
        }
        val _columnsSleepEpochs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSleepEpochs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepEpochs.put("sessionId", TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepEpochs.put("timestampMillis", TableInfo.Column("timestampMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepEpochs.put("stage", TableInfo.Column("stage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepEpochs.put("movementMagnitude", TableInfo.Column("movementMagnitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSleepEpochs.put("movementVariability", TableInfo.Column("movementVariability", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSleepEpochs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysSleepEpochs.add(TableInfo.ForeignKey("sleep_sessions", "CASCADE", "NO ACTION", listOf("sessionId"), listOf("id")))
        val _indicesSleepEpochs: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesSleepEpochs.add(TableInfo.Index("index_sleep_epochs_sessionId", false, listOf("sessionId"), listOf("ASC")))
        val _infoSleepEpochs: TableInfo = TableInfo("sleep_epochs", _columnsSleepEpochs, _foreignKeysSleepEpochs, _indicesSleepEpochs)
        val _existingSleepEpochs: TableInfo = read(connection, "sleep_epochs")
        if (!_infoSleepEpochs.equals(_existingSleepEpochs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |sleep_epochs(dev.vic41148.somn.core.data.database.entity.SleepEpochEntity).
              | Expected:
              |""".trimMargin() + _infoSleepEpochs + """
              |
              | Found:
              |""".trimMargin() + _existingSleepEpochs)
        }
        val _columnsAlarms: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAlarms.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("hour", TableInfo.Column("hour", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("minute", TableInfo.Column("minute", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("label", TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("isEnabled", TableInfo.Column("isEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("repeatDays", TableInfo.Column("repeatDays", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("wakeWindowMinutes", TableInfo.Column("wakeWindowMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("snoozeDurationMinutes", TableInfo.Column("snoozeDurationMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("maxSnoozeCount", TableInfo.Column("maxSnoozeCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("soundUri", TableInfo.Column("soundUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("vibrationEnabled", TableInfo.Column("vibrationEnabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("gradualVolumeSeconds", TableInfo.Column("gradualVolumeSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAlarms.put("captchaType", TableInfo.Column("captchaType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAlarms: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAlarms: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAlarms: TableInfo = TableInfo("alarms", _columnsAlarms, _foreignKeysAlarms, _indicesAlarms)
        val _existingAlarms: TableInfo = read(connection, "alarms")
        if (!_infoAlarms.equals(_existingAlarms)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |alarms(dev.vic41148.somn.core.data.database.entity.AlarmEntity).
              | Expected:
              |""".trimMargin() + _infoAlarms + """
              |
              | Found:
              |""".trimMargin() + _existingAlarms)
        }
        val _columnsTags: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTags.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("category", TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("color", TableInfo.Column("color", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("icon", TableInfo.Column("icon", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTags.put("isArchived", TableInfo.Column("isArchived", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTags: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTags: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTags: TableInfo = TableInfo("tags", _columnsTags, _foreignKeysTags, _indicesTags)
        val _existingTags: TableInfo = read(connection, "tags")
        if (!_infoTags.equals(_existingTags)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tags(dev.vic41148.somn.core.data.database.entity.TagEntity).
              | Expected:
              |""".trimMargin() + _infoTags + """
              |
              | Found:
              |""".trimMargin() + _existingTags)
        }
        val _columnsSessionTags: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSessionTags.put("sessionId", TableInfo.Column("sessionId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSessionTags.put("tagId", TableInfo.Column("tagId", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSessionTags: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysSessionTags.add(TableInfo.ForeignKey("sleep_sessions", "CASCADE", "NO ACTION", listOf("sessionId"), listOf("id")))
        _foreignKeysSessionTags.add(TableInfo.ForeignKey("tags", "CASCADE", "NO ACTION", listOf("tagId"), listOf("id")))
        val _indicesSessionTags: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesSessionTags.add(TableInfo.Index("index_session_tags_sessionId", false, listOf("sessionId"), listOf("ASC")))
        _indicesSessionTags.add(TableInfo.Index("index_session_tags_tagId", false, listOf("tagId"), listOf("ASC")))
        val _infoSessionTags: TableInfo = TableInfo("session_tags", _columnsSessionTags, _foreignKeysSessionTags, _indicesSessionTags)
        val _existingSessionTags: TableInfo = read(connection, "session_tags")
        if (!_infoSessionTags.equals(_existingSessionTags)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |session_tags(dev.vic41148.somn.core.data.database.entity.SessionTagEntity).
              | Expected:
              |""".trimMargin() + _infoSessionTags + """
              |
              | Found:
              |""".trimMargin() + _existingSessionTags)
        }
        val _columnsUserProfile: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsUserProfile.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("dateOfBirth", TableInfo.Column("dateOfBirth", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("biologicalSex", TableInfo.Column("biologicalSex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("lifeStage", TableInfo.Column("lifeStage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("chronotype", TableInfo.Column("chronotype", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("chronotypeMeqScore", TableInfo.Column("chronotypeMeqScore", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("adhdMode", TableInfo.Column("adhdMode", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("asdMode", TableInfo.Column("asdMode", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("medicationTracking", TableInfo.Column("medicationTracking", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("targetSleepHours", TableInfo.Column("targetSleepHours", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("pregnancyTrimester", TableInfo.Column("pregnancyTrimester", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("pregnancyDueDate", TableInfo.Column("pregnancyDueDate", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("cycleLength", TableInfo.Column("cycleLength", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("lastPeriodStartDate", TableInfo.Column("lastPeriodStartDate", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("shiftWorker", TableInfo.Column("shiftWorker", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("timezoneId", TableInfo.Column("timezoneId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsUserProfile.put("onboardingCompleted", TableInfo.Column("onboardingCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysUserProfile: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesUserProfile: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoUserProfile: TableInfo = TableInfo("user_profile", _columnsUserProfile, _foreignKeysUserProfile, _indicesUserProfile)
        val _existingUserProfile: TableInfo = read(connection, "user_profile")
        if (!_infoUserProfile.equals(_existingUserProfile)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |user_profile(dev.vic41148.somn.core.data.database.entity.UserProfileEntity).
              | Expected:
              |""".trimMargin() + _infoUserProfile + """
              |
              | Found:
              |""".trimMargin() + _existingUserProfile)
        }
        val _columnsHabitLogs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsHabitLogs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("date", TableInfo.Column("date", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("entryType", TableInfo.Column("entryType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("caffeineMg", TableInfo.Column("caffeineMg", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("caffeineSource", TableInfo.Column("caffeineSource", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("alcoholUnits", TableInfo.Column("alcoholUnits", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("exerciseType", TableInfo.Column("exerciseType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("exerciseDurationMinutes", TableInfo.Column("exerciseDurationMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("exerciseIntensity", TableInfo.Column("exerciseIntensity", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("stressLevel", TableInfo.Column("stressLevel", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("medicationName", TableInfo.Column("medicationName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("medicationDose", TableInfo.Column("medicationDose", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("medicationIsStimulant", TableInfo.Column("medicationIsStimulant", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("timeOfDayHour", TableInfo.Column("timeOfDayHour", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("timeOfDayMinute", TableInfo.Column("timeOfDayMinute", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHabitLogs.put("notes", TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHabitLogs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesHabitLogs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoHabitLogs: TableInfo = TableInfo("habit_logs", _columnsHabitLogs, _foreignKeysHabitLogs, _indicesHabitLogs)
        val _existingHabitLogs: TableInfo = read(connection, "habit_logs")
        if (!_infoHabitLogs.equals(_existingHabitLogs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |habit_logs(dev.vic41148.somn.core.data.database.entity.HabitLogEntity).
              | Expected:
              |""".trimMargin() + _infoHabitLogs + """
              |
              | Found:
              |""".trimMargin() + _existingHabitLogs)
        }
        val _columnsAudioEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAudioEvents.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioEvents.put("sessionId", TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioEvents.put("timestampMillis", TableInfo.Column("timestampMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioEvents.put("durationSeconds", TableInfo.Column("durationSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioEvents.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAudioEvents.put("intensityDecibels", TableInfo.Column("intensityDecibels", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAudioEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAudioEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAudioEvents: TableInfo = TableInfo("audio_events", _columnsAudioEvents, _foreignKeysAudioEvents, _indicesAudioEvents)
        val _existingAudioEvents: TableInfo = read(connection, "audio_events")
        if (!_infoAudioEvents.equals(_existingAudioEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |audio_events(dev.vic41148.somn.core.data.database.entity.AudioEventEntity).
              | Expected:
              |""".trimMargin() + _infoAudioEvents + """
              |
              | Found:
              |""".trimMargin() + _existingAudioEvents)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "sleep_sessions", "sleep_epochs", "alarms", "tags", "session_tags", "user_profile", "habit_logs", "audio_events")
  }

  public override fun clearAllTables() {
    super.performClear(true, "sleep_sessions", "sleep_epochs", "alarms", "tags", "session_tags", "user_profile", "habit_logs", "audio_events")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(SleepSessionDao::class, SleepSessionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SleepEpochDao::class, SleepEpochDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(AlarmDao::class, AlarmDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TagDao::class, TagDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(UserProfileDao::class, UserProfileDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(HabitLogDao::class, HabitLogDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(AudioEventDao::class, AudioEventDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun sleepSessionDao(): SleepSessionDao = _sleepSessionDao.value

  public override fun sleepEpochDao(): SleepEpochDao = _sleepEpochDao.value

  public override fun alarmDao(): AlarmDao = _alarmDao.value

  public override fun tagDao(): TagDao = _tagDao.value

  public override fun userProfileDao(): UserProfileDao = _userProfileDao.value

  public override fun habitLogDao(): HabitLogDao = _habitLogDao.value

  public override fun audioEventDao(): AudioEventDao = _audioEventDao.value
}
