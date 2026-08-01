package dev.vic41148.somn.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Thin wrapper over the Health Connect SDK — permission set, client lifecycle, raw record
 * read/insert. Deliberately platform-typed (returns HC `Record` subtypes); mapping to Somn's
 * own domain models happens one layer up in `core:data`'s `HealthConnectRepository`, per the
 * "core:health is a pure adapter" architecture decision — no Room/domain knowledge here.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** HEALTH-01: external vitals a paired wearable wrote. HEALTH-04: existing sleep records, to detect dupes. */
        val READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(SkinTemperatureRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )

        /** HEALTH-02: write completed Somn sessions to Health Connect. */
        val WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(SleepSessionRecord::class)
        )

        val ALL_PERMISSIONS: Set<String> = READ_PERMISSIONS + WRITE_PERMISSIONS
    }

    /** True when the Health Connect provider (platform-builtin on 14+, or the standalone app pre-14) is installed. */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun clientOrNull(): HealthConnectClient? =
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    /**
     * HEALTH-03: re-checks the actual OS-granted permission set on every call rather than caching
     * a stale "was granted once" flag — the user can revoke Health Connect access at any time from
     * system settings, entirely outside Somn's control.
     */
    suspend fun hasAllPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(ALL_PERMISSIONS)
    }

    suspend fun hasWritePermission(): Boolean {
        val client = clientOrNull() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(WRITE_PERMISSIONS)
    }

    /** [ActivityResultContract] for the Health Connect permission request flow — hosted from a Composable/Activity. */
    fun requestPermissionsContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun <T : Record> readRecords(recordType: KClass<T>, start: Instant, end: Instant): List<T> {
        val client = clientOrNull() ?: return emptyList()
        return client.readRecords(
            ReadRecordsRequest(recordType = recordType, timeRangeFilter = TimeRangeFilter.between(start, end))
        ).records
    }

    /** Returns the Health Connect-assigned record IDs of the inserted records, in the same order. */
    suspend fun insertRecords(records: List<Record>): List<String> {
        val client = clientOrNull() ?: return emptyList()
        return client.insertRecords(records).recordIdsList
    }

    suspend fun deleteRecord(recordType: KClass<out Record>, recordId: String) {
        val client = clientOrNull() ?: return
        client.deleteRecords(recordType = recordType, recordIdsList = listOf(recordId), clientRecordIdsList = emptyList())
    }
}
