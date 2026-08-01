package dev.vic41148.somn.feature.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vic41148.somn.core.data.repository.AlarmRepository
import dev.vic41148.somn.core.domain.repository.AlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // onReceive() returning ends the receiver's process-priority window — a bare
            // fire-and-forget coroutine here can get killed by the OS mid-reschedule right after
            // boot, before any alarms are actually re-armed, silently losing every alarm across
            // that reboot. goAsync() tells the OS to keep the process alive until finish() below.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarms = alarmRepository.getEnabledAlarms()
                    alarms.forEach { alarm ->
                        alarmScheduler.schedule(alarm)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
