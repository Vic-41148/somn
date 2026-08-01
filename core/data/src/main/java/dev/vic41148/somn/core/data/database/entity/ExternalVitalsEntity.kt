package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * One row per sleep session, aggregating the external vitals (HR/HRV/SpO2/skin temp) a paired
 * wearable wrote into Health Connect during that session's time window (HEALTH-01). Mirrors the
 * existing pattern of storing a per-session aggregate rather than raw samples (see
 * [SleepSessionEntity.avgBreathingRateBrpm]) — Somn doesn't need per-beat HR history, just the
 * night's summary.
 */
@Entity(
    tableName = "external_vitals",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExternalVitalsEntity(
    @PrimaryKey
    val sessionId: Long,
    val avgHeartRateBpm: Float? = null,
    val restingHeartRateBpm: Float? = null,
    val avgHeartRateVariabilityMs: Float? = null,
    val avgSpo2Percent: Float? = null,
    val minSpo2Percent: Float? = null,
    val avgSkinTemperatureCelsius: Float? = null,
    val sourceApp: String? = null
)
