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
            CoroutineScope(Dispatchers.IO).launch {
                val alarms = alarmRepository.getEnabledAlarms()
                alarms.forEach { alarm ->
                    alarmScheduler.schedule(alarm)
                }
            }
        }
    }
}
