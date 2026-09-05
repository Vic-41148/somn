package dev.vic41148.somn.feature.alarm.service

import android.content.Context
import dev.vic41148.somn.core.domain.model.Alarm
import dev.vic41148.somn.core.domain.repository.AlarmScheduler
import dev.vic41148.somn.feature.alarm.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject

class AlarmSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlarmScheduler {

    override fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm.id)
            return
        }

        val now = Calendar.getInstance()
        val nextTriggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.repeatDays.isEmpty()) {
            // One-time alarm
            if (nextTriggerTime.before(now)) {
                nextTriggerTime.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            // Recurring alarm
            // Find the closest day in the future that matches a repeat day
            var daysToAdd = 0
            while (true) {
                val checkDayInfo = Calendar.getInstance().apply { timeInMillis = nextTriggerTime.timeInMillis }
                checkDayInfo.add(Calendar.DAY_OF_YEAR, daysToAdd)
                
                val currentDayOfWeek = checkDayInfo.get(Calendar.DAY_OF_WEEK)
                
                // If it is today but the time has already passed, we must look for the next occurrence
                if (daysToAdd == 0 && nextTriggerTime.before(now)) {
                    daysToAdd++
                    continue
                }

                if (alarm.repeatDays.contains(currentDayOfWeek)) {
                    // Found next trigger day
                    nextTriggerTime.add(Calendar.DAY_OF_YEAR, daysToAdd)
                    break
                }
                daysToAdd++
                if (daysToAdd > 7) break // Safety net, shouldn't happen if repeatDays is not empty
            }
        }

        AlarmReceiver.scheduleAlarm(
            context = context,
            alarmId = alarm.id,
            timeInMillis = nextTriggerTime.timeInMillis,
            label = alarm.label,
            vibration = alarm.vibrationEnabled,
            gradualSeconds = alarm.gradualVolumeSeconds,
            captchaType = alarm.captchaType.name,
            wakeWindowMinutes = alarm.wakeWindowMinutes
        )
    }

    override fun cancel(alarmId: Long) {
        AlarmReceiver.cancelAlarm(context, alarmId)
    }
}
