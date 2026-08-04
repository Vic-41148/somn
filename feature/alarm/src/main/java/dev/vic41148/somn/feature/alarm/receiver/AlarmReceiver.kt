package dev.vic41148.somn.feature.alarm.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vic41148.somn.feature.alarm.service.AlarmService
import java.util.Calendar

/**
 * Receives alarm triggers from AlarmManager and starts the AlarmService.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_VIBRATION = "vibration"
        const val EXTRA_GRADUAL_SECONDS = "gradual_seconds"
        const val EXTRA_CAPTCHA_TYPE = "captcha_type"
        const val EXTRA_WAKE_WINDOW_MINUTES = "wake_window_minutes"

        /** Sentinel returned when the intent carries no per-alarm wake window (legacy pending intents). */
        const val NO_WAKE_WINDOW = -1

        fun scheduleAlarm(
            context: Context,
            alarmId: Long,
            timeInMillis: Long,
            label: String,
            vibration: Boolean,
            gradualSeconds: Int,
            captchaType: String,
            wakeWindowMinutes: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
                putExtra(EXTRA_VIBRATION, vibration)
                putExtra(EXTRA_GRADUAL_SECONDS, gradualSeconds)
                putExtra(EXTRA_CAPTCHA_TYPE, captchaType)
                putExtra(EXTRA_WAKE_WINDOW_MINUTES, wakeWindowMinutes)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(timeInMillis, pendingIntent),
                pendingIntent
            )
        }

        fun cancelAlarm(context: Context, alarmId: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(EXTRA_ALARM_ID, intent.getLongExtra(EXTRA_ALARM_ID, -1))
            putExtra(EXTRA_ALARM_LABEL, intent.getStringExtra(EXTRA_ALARM_LABEL))
            putExtra(EXTRA_VIBRATION, intent.getBooleanExtra(EXTRA_VIBRATION, true))
            putExtra(EXTRA_GRADUAL_SECONDS, intent.getIntExtra(EXTRA_GRADUAL_SECONDS, 60))
            putExtra(EXTRA_CAPTCHA_TYPE, intent.getStringExtra(EXTRA_CAPTCHA_TYPE))
            putExtra(EXTRA_WAKE_WINDOW_MINUTES, intent.getIntExtra(EXTRA_WAKE_WINDOW_MINUTES, NO_WAKE_WINDOW))
        }
        context.startForegroundService(serviceIntent)
    }
}
