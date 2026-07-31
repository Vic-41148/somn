package dev.vic41148.somn.core.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs every migration in [ALL_MIGRATIONS] against a real pre-migration schema and
 * checks the resulting table survives Room's own validation. Each test seeds a row
 * at the source version so `ADD COLUMN ... DEFAULT` values are actually exercised,
 * not just the empty-table path.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SleepDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_addsHabitLogsTable() {
        helper.createDatabase(TEST_DB, 2).apply { close() }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        val cursor = db.query("SELECT * FROM habit_logs")
        assertThat(cursor.columnCount).isEqualTo(16)
        cursor.close()
    }

    @Test
    fun migrate3To4_addsSessionContextColumnsWithDefaults() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO sleep_sessions
                (id, startTimeMillis, endTimeMillis, sleepDurationMinutes, timeInBedMinutes,
                 sleepEfficiency, sleepOnsetMinutes, wakeEvents, deepSleepPercent, lightSleepPercent,
                 remSleepPercent, sleepScore, moodRating, notes, isCompleted)
                VALUES (1, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0, 0, '', 1)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        val cursor = db.query("SELECT timezoneId, isHomeSleep, alarmUsed FROM sleep_sessions WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(0)).isEqualTo("UTC")
        assertThat(cursor.getInt(1)).isEqualTo(1)
        assertThat(cursor.getInt(2)).isEqualTo(0)
        cursor.close()
    }

    @Test
    fun migrate4To5_addsAudioEventsTable() {
        helper.createDatabase(TEST_DB, 4).apply { close() }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        val cursor = db.query("SELECT * FROM audio_events")
        assertThat(cursor.columnCount).isEqualTo(6)
        cursor.close()
    }

    @Test
    fun migrate5To6_addsNullableBreathingRate() {
        helper.createDatabase(TEST_DB, 5).apply { close() }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
        val cursor = db.query("SELECT avgBreathingRateBrpm FROM sleep_sessions")
        assertThat(cursor.columnCount).isEqualTo(1)
        cursor.close()
    }

    @Test
    fun migrate6To7_addsCoughCountAndClipPath() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO audio_events (id, sessionId, timestampMillis, durationSeconds, type, intensityDecibels) " +
                    "VALUES (1, 1, 0, 1, 'SNORE', 50)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)
        val sessionCursor = db.query("SELECT coughEventCount FROM sleep_sessions")
        assertThat(sessionCursor.columnCount).isEqualTo(1)
        sessionCursor.close()

        val audioCursor = db.query("SELECT clipPath FROM audio_events WHERE id = 1")
        assertThat(audioCursor.moveToFirst()).isTrue()
        assertThat(audioCursor.isNull(0)).isTrue()
        audioCursor.close()
    }

    @Test
    fun migrate7To8_isNoOp() {
        helper.createDatabase(TEST_DB, 7).apply { close() }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
    }

    @Test
    fun migrate8To9_addsSyncedToNasDefaultFalse() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO audio_events (id, sessionId, timestampMillis, durationSeconds, type, intensityDecibels) " +
                    "VALUES (1, 1, 0, 1, 'SNORE', 50)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)
        val cursor = db.query("SELECT syncedToNas FROM audio_events WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(0)).isEqualTo(0)
        cursor.close()
    }

    @Test
    fun migrate9To10_addsIsPartialDefaultFalse() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO sleep_sessions " +
                    "(id, startTimeMillis, endTimeMillis, sleepDurationMinutes, timeInBedMinutes, " +
                    "sleepEfficiency, sleepOnsetMinutes, wakeEvents, deepSleepPercent, lightSleepPercent, " +
                    "remSleepPercent, sleepScore, moodRating, notes, isCompleted, timezoneId, isHomeSleep, " +
                    "alarmUsed, coughEventCount) " +
                    "VALUES (1, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0, 0, '', 1, 'UTC', 1, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        val cursor = db.query("SELECT isPartial FROM sleep_sessions WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(0)).isEqualTo(0)
        cursor.close()
    }

    @Test
    fun migrate10To11_dropsShiftWorkerPreservingData() {
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                "INSERT INTO user_profile " +
                    "(id, dateOfBirth, biologicalSex, lifeStage, chronotype, chronotypeMeqScore, " +
                    "adhdMode, asdMode, medicationTracking, targetSleepHours, pregnancyTrimester, " +
                    "pregnancyDueDate, cycleLength, lastPeriodStartDate, shiftWorker, timezoneId, " +
                    "onboardingCompleted) " +
                    "VALUES (1, '1990-01-01', 'FEMALE', 'DEFAULT', 'UNKNOWN', NULL, " +
                    "0, 0, 0, 8.0, NULL, NULL, 28, NULL, 1, 'UTC', 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)
        val cursor = db.query("SELECT dateOfBirth, biologicalSex, onboardingCompleted FROM user_profile WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(0)).isEqualTo("1990-01-01")
        assertThat(cursor.getString(1)).isEqualTo("FEMALE")
        assertThat(cursor.getInt(2)).isEqualTo(1)
        cursor.close()

        val columnCursor = db.query("SELECT * FROM user_profile")
        assertThat(columnCursor.columnNames.toList()).doesNotContain("shiftWorker")
        columnCursor.close()
    }

    @Test
    fun migrate11To12_addsSessionTypeAndOversleepWithDefaults() {
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                "INSERT INTO sleep_sessions " +
                    "(id, startTimeMillis, endTimeMillis, sleepDurationMinutes, timeInBedMinutes, " +
                    "sleepEfficiency, sleepOnsetMinutes, wakeEvents, deepSleepPercent, lightSleepPercent, " +
                    "remSleepPercent, sleepScore, moodRating, notes, isCompleted, timezoneId, isHomeSleep, " +
                    "alarmUsed, coughEventCount, isPartial) " +
                    "VALUES (1, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0, 0, '', 1, 'UTC', 1, 0, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)
        val cursor = db.query("SELECT sessionType, isOversleep FROM sleep_sessions WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(0)).isEqualTo("MAIN_SLEEP")
        assertThat(cursor.getInt(1)).isEqualTo(0)
        cursor.close()
    }

    @Test
    fun migrate12To13_addsHealthConnectRecordIdAndExternalVitalsTable() {
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL(
                "INSERT INTO sleep_sessions " +
                    "(id, startTimeMillis, endTimeMillis, sleepDurationMinutes, timeInBedMinutes, " +
                    "sleepEfficiency, sleepOnsetMinutes, wakeEvents, deepSleepPercent, lightSleepPercent, " +
                    "remSleepPercent, sleepScore, moodRating, notes, isCompleted, timezoneId, isHomeSleep, " +
                    "alarmUsed, coughEventCount, isPartial, sessionType, isOversleep) " +
                    "VALUES (1, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0, 0, '', 1, 'UTC', 1, 0, 0, 0, 'MAIN_SLEEP', 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)
        val sessionCursor = db.query("SELECT healthConnectRecordId FROM sleep_sessions WHERE id = 1")
        assertThat(sessionCursor.moveToFirst()).isTrue()
        assertThat(sessionCursor.isNull(0)).isTrue()
        sessionCursor.close()

        val vitalsCursor = db.query("SELECT * FROM external_vitals")
        assertThat(vitalsCursor.columnCount).isEqualTo(8)
        vitalsCursor.close()
    }

    @Test
    fun migrateAll_2To13_inOneShot() {
        helper.createDatabase(TEST_DB, 2).apply { close() }

        helper.runMigrationsAndValidate(TEST_DB, 13, true, *ALL_MIGRATIONS)
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
