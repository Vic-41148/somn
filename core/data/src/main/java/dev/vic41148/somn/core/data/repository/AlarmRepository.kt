package dev.vic41148.somn.core.data.repository

import dev.vic41148.somn.core.data.database.dao.AlarmDao
import dev.vic41148.somn.core.data.database.entity.AlarmEntity
import dev.vic41148.somn.core.domain.model.Alarm
import dev.vic41148.somn.core.domain.model.CaptchaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao
) {

    suspend fun createAlarm(alarm: Alarm): Long {
        return alarmDao.insert(alarm.toEntity())
    }

    suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.update(alarm.toEntity())
    }

    suspend fun deleteAlarm(alarm: Alarm) {
        alarmDao.delete(alarm.toEntity())
    }

    suspend fun getAlarm(id: Long): Alarm? {
        return alarmDao.getById(id)?.toDomain()
    }

    fun observeAlarms(): Flow<List<Alarm>> {
        return alarmDao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getEnabledAlarms(): List<Alarm> {
        return alarmDao.getEnabledAlarms().map { it.toDomain() }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        alarmDao.setEnabled(id, enabled)
    }

    /**
     * The enabled alarm chronologically closest to firing from now, wrapping past-due
     * hour:minute values to tomorrow. This used to be a SQL `ORDER BY hour ASC, minute ASC
     * LIMIT 1` query, which picked the smallest hour:minute rather than the smallest
     * time-until-next-fire — e.g. at 22:00 with alarms enabled at 06:00 and 23:00, it always
     * returned 06:00 (23h away) over 23:00 (1h away). This feeds SleepTrackingService's smart
     * wake-window, so the wrong alarm here made the app watch for the wrong target time.
     */
    suspend fun getNextAlarm(): Alarm? {
        val now = java.util.Calendar.getInstance()
        val nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        return getEnabledAlarms().minByOrNull { alarm ->
            val alarmMinutes = alarm.hour * 60 + alarm.minute
            ((alarmMinutes - nowMinutes) + 1440) % 1440
        }
    }

    // --- Mappers ---

    private fun AlarmEntity.toDomain() = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        label = label,
        isEnabled = isEnabled,
        repeatDays = if (repeatDays.isBlank()) emptySet()
            else repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
        wakeWindowMinutes = wakeWindowMinutes,
        snoozeDurationMinutes = snoozeDurationMinutes,
        maxSnoozeCount = maxSnoozeCount,
        soundUri = soundUri,
        vibrationEnabled = vibrationEnabled,
        gradualVolumeSeconds = gradualVolumeSeconds,
        captchaType = try { CaptchaType.valueOf(captchaType) } catch (e: Exception) { CaptchaType.NONE }
    )

    private fun Alarm.toEntity() = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        label = label,
        isEnabled = isEnabled,
        repeatDays = repeatDays.joinToString(","),
        wakeWindowMinutes = wakeWindowMinutes,
        snoozeDurationMinutes = snoozeDurationMinutes,
        maxSnoozeCount = maxSnoozeCount,
        soundUri = soundUri,
        vibrationEnabled = vibrationEnabled,
        gradualVolumeSeconds = gradualVolumeSeconds,
        captchaType = captchaType.name
    )
}
