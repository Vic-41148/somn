package dev.vic41148.somn.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written schema migrations for [SleepDatabase].
 *
 * Every migration is derived from the exported schema JSONs in `core/data/schemas/`.
 * Defaults on `ADD COLUMN` mirror the Kotlin property defaults on the entity so that
 * rows written before the column existed match what the app would have written.
 *
 * When bumping the DB version: export the new schema, add a migration here, add it to
 * [ALL_MIGRATIONS], and add a case to `MigrationTest`.
 */

/** v2 → v3: habit logging introduced. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `habit_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `date` TEXT NOT NULL,
                `entryType` TEXT NOT NULL,
                `caffeineMg` INTEGER,
                `caffeineSource` TEXT,
                `alcoholUnits` REAL,
                `exerciseType` TEXT,
                `exerciseDurationMinutes` INTEGER,
                `exerciseIntensity` TEXT,
                `stressLevel` INTEGER,
                `medicationName` TEXT,
                `medicationDose` TEXT,
                `medicationIsStimulant` INTEGER,
                `timeOfDayHour` INTEGER,
                `timeOfDayMinute` INTEGER,
                `notes` TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v3 → v4: session context — timezone, home-vs-away, whether an alarm ended it. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `timezoneId` TEXT NOT NULL DEFAULT 'UTC'")
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `isHomeSleep` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `alarmUsed` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4 → v5: audio event capture introduced. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audio_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sessionId` INTEGER NOT NULL,
                `timestampMillis` INTEGER NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `intensityDecibels` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/** v5 → v6: mic-derived breathing rate stored per session. Nullable — older sessions have none. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `avgBreathingRateBrpm` REAL")
    }
}

/** v6 → v7: cough tally on the session, retained clip path on the audio event. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `coughEventCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `audio_events` ADD COLUMN `clipPath` TEXT")
    }
}

/**
 * v7 → v8: no schema change.
 *
 * The version was bumped without altering any table. Room still requires a path from 7
 * to 8, so this is intentionally empty rather than missing.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

/** v8 → v9: NAS sync flag on audio events. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `audio_events` ADD COLUMN `syncedToNas` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v9 → v10: incomplete-night detection flag on sessions (REL-02). */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `isPartial` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v10 → v11: drop the dead `shiftWorker` flag (REL-08) — never wired to any UI, real
 * shift-work behavior is v2/next-milestone scope. SQLite's `ALTER TABLE ... DROP COLUMN`
 * requires SQLite 3.35+ (2021), which predates the bundled SQLite on this app's minSdk 26
 * (Android 8) devices, so the column is dropped via the standard recreate-copy-swap pattern.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No SQL-level DEFAULTs here — Room's own generated schema doesn't declare column
        // defaults (they're applied at the Kotlin/insert level), so adding DEFAULT clauses
        // would make TableInfo validation fail against the entity's expected schema.
        db.execSQL(
            """
            CREATE TABLE `user_profile_new` (
                `id` INTEGER NOT NULL,
                `dateOfBirth` TEXT,
                `biologicalSex` TEXT NOT NULL,
                `lifeStage` TEXT NOT NULL,
                `chronotype` TEXT NOT NULL,
                `chronotypeMeqScore` INTEGER,
                `adhdMode` INTEGER NOT NULL,
                `asdMode` INTEGER NOT NULL,
                `medicationTracking` INTEGER NOT NULL,
                `targetSleepHours` REAL NOT NULL,
                `pregnancyTrimester` INTEGER,
                `pregnancyDueDate` TEXT,
                `cycleLength` INTEGER NOT NULL,
                `lastPeriodStartDate` TEXT,
                `timezoneId` TEXT NOT NULL,
                `onboardingCompleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `user_profile_new`
                (id, dateOfBirth, biologicalSex, lifeStage, chronotype, chronotypeMeqScore,
                 adhdMode, asdMode, medicationTracking, targetSleepHours, pregnancyTrimester,
                 pregnancyDueDate, cycleLength, lastPeriodStartDate, timezoneId, onboardingCompleted)
            SELECT
                id, dateOfBirth, biologicalSex, lifeStage, chronotype, chronotypeMeqScore,
                adhdMode, asdMode, medicationTracking, targetSleepHours, pregnancyTrimester,
                pregnancyDueDate, cycleLength, lastPeriodStartDate, timezoneId, onboardingCompleted
            FROM `user_profile`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `user_profile`")
        db.execSQL("ALTER TABLE `user_profile_new` RENAME TO `user_profile`")
    }
}

/** v11 → v12: session type (main sleep/nap/commute/shift) and oversleep flag (SESS-01/03). */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `sessionType` TEXT NOT NULL DEFAULT 'MAIN_SLEEP'")
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `isOversleep` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v12 → v13: Health Connect integration — external vitals table + dedup marker on sessions (HEALTH-01/02/04). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sleep_sessions` ADD COLUMN `healthConnectRecordId` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `external_vitals` (
                `sessionId` INTEGER PRIMARY KEY NOT NULL,
                `avgHeartRateBpm` REAL,
                `restingHeartRateBpm` REAL,
                `avgHeartRateVariabilityMs` REAL,
                `avgSpo2Percent` REAL,
                `minSpo2Percent` REAL,
                `avgSkinTemperatureCelsius` REAL,
                `sourceApp` TEXT,
                FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

/**
 * Every migration, in order. Pass to `RoomDatabase.Builder.addMigrations`.
 *
 * There is no path from v1: that schema predates `exportSchema` and was never captured,
 * so a v1 database falls back to destructive recreation. No release ever shipped v1.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13
)
