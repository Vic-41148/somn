package dev.vic41148.somn.core.data.repository

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import dev.vic41148.somn.core.domain.model.ExternalVitalsSnapshot
import dev.vic41148.somn.core.domain.model.HealthConnectStatus
import dev.vic41148.somn.core.domain.model.SleepEpoch
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.model.SleepStage
import dev.vic41148.somn.core.health.HealthConnectManager
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps between Health Connect's platform record types and Somn's own domain models.
 * `core:health`'s [HealthConnectManager] stays a pure SDK adapter — all Somn-specific
 * aggregation/dedup logic (HEALTH-01/02/04) lives here, one layer up, per the
 * "core:health is a pure adapter" architecture decision.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val sleepRepository: SleepRepository
) {

    /** HEALTH-03: re-checked live on every call — never trust a cached "was connected" flag. */
    suspend fun getStatus(): HealthConnectStatus {
        if (!healthConnectManager.isAvailable()) return HealthConnectStatus.UNAVAILABLE
        return if (healthConnectManager.hasAllPermissions()) {
            HealthConnectStatus.AUTHORIZED
        } else {
            HealthConnectStatus.NOT_AUTHORIZED
        }
    }

    /** Permission set the settings UI's launcher requests via [permissionsContract]. */
    val requiredPermissions: Set<String> get() = HealthConnectManager.ALL_PERMISSIONS

    /** Hosted from a Composable via `rememberLauncherForActivityResult` to drive the OS permission sheet. */
    fun permissionsContract() = healthConnectManager.requestPermissionsContract()

    /**
     * HEALTH-01: reads HR/HRV/SpO2/skin-temperature records any source wrote in [start, end)
     * and stores the night's aggregate. No-ops (rather than throwing) when not authorized —
     * a revoked grant should degrade silently, not break session completion.
     */
    suspend fun syncVitalsForSession(sessionId: Long, start: Instant, end: Instant) {
        if (getStatus() != HealthConnectStatus.AUTHORIZED) return

        val heartRateRecords = healthConnectManager.readRecords(HeartRateRecord::class, start, end)
        val hrvRecords = healthConnectManager.readRecords(HeartRateVariabilityRmssdRecord::class, start, end)
        val spo2Records = healthConnectManager.readRecords(OxygenSaturationRecord::class, start, end)
        val skinTempRecords = healthConnectManager.readRecords(SkinTemperatureRecord::class, start, end)

        val bpmSamples = heartRateRecords.flatMap { it.samples }.map { it.beatsPerMinute.toFloat() }
        val avgHr = bpmSamples.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val restingHr = bpmSamples.minOrNull()

        val hrvValues = hrvRecords.map { it.heartRateVariabilityMillis.toFloat() }
        val avgHrv = hrvValues.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        val spo2Values = spo2Records.map { it.percentage.value.toFloat() }
        val avgSpo2 = spo2Values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val minSpo2 = spo2Values.minOrNull()

        // Temperature's Kotlin property is `inCelsius`; its getter carries @JvmName("getCelsius"),
        // so `.getCelsius()` and `.celsius` both fail from Kotlin (the latter is the companion's
        // factory function, not a property). baseline is nullable, hence mapNotNull.
        val skinTempValues = skinTempRecords.mapNotNull { it.baseline?.inCelsius?.toFloat() }
        val avgSkinTemp = skinTempValues.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        val sourceApp = heartRateRecords.firstOrNull()?.metadata?.dataOrigin?.packageName
            ?: hrvRecords.firstOrNull()?.metadata?.dataOrigin?.packageName
            ?: spo2Records.firstOrNull()?.metadata?.dataOrigin?.packageName

        val snapshot = ExternalVitalsSnapshot(
            sessionId = sessionId,
            avgHeartRateBpm = avgHr,
            restingHeartRateBpm = restingHr,
            avgHeartRateVariabilityMs = avgHrv,
            avgSpo2Percent = avgSpo2,
            minSpo2Percent = minSpo2,
            avgSkinTemperatureCelsius = avgSkinTemp,
            sourceApp = sourceApp
        )

        if (snapshot.hasAnyData) {
            sleepRepository.upsertExternalVitals(snapshot)
        }
    }

    /**
     * HEALTH-02: writes a completed session as a [SleepSessionRecord] with per-epoch stages.
     * HEALTH-04: skips outright if [SleepSession.healthConnectRecordId] is already set — a
     * session is written to Health Connect at most once, regardless of how many times sync runs.
     * Also skips if another source (a wearable's own app, e.g. Fitbit/Samsung Health) already
     * wrote a sleep record overlapping this window — since [SleepSession.healthConnectRecordId]
     * is null here, any existing record in range can only have come from elsewhere, and writing
     * Somn's own on top would create two overlapping sleep blobs in the user's aggregated timeline.
     */
    suspend fun writeSleepSession(session: SleepSession, epochs: List<SleepEpoch>) {
        if (session.healthConnectRecordId != null) return
        if (getStatus() != HealthConnectStatus.AUTHORIZED) return
        if (epochs.isEmpty()) return

        val start = Instant.ofEpochMilli(session.startTimeMillis)
        val end = Instant.ofEpochMilli(session.endTimeMillis)
        val existingRecords = healthConnectManager.readRecords(SleepSessionRecord::class, start, end)
        val hasOverlap = existingRecords.any { it.startTime < end && it.endTime > start }
        if (hasOverlap) return

        val epochDurationMillis = 30_000L
        val stages = epochs.map { epoch ->
            SleepSessionRecord.Stage(
                startTime = Instant.ofEpochMilli(epoch.timestampMillis),
                endTime = Instant.ofEpochMilli(epoch.timestampMillis + epochDurationMillis),
                stage = epoch.stage.toHealthConnectStageType()
            )
        }

        val record = SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            title = "Somn Sleep Session",
            stages = stages,
            metadata = Metadata()
        )

        val recordId = healthConnectManager.insertRecords(listOf(record)).firstOrNull() ?: return
        sleepRepository.updateSession(session.copy(healthConnectRecordId = recordId))
    }

    private fun SleepStage.toHealthConnectStageType(): Int = when (this) {
        SleepStage.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
        SleepStage.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
        SleepStage.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
        SleepStage.REM -> SleepSessionRecord.STAGE_TYPE_REM
        SleepStage.UNKNOWN -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
    }
}
