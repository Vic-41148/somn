package dev.vic41148.somn.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.AlarmRepository
import dev.vic41148.somn.core.domain.model.Alarm
import dev.vic41148.somn.core.domain.model.CaptchaType
import dev.vic41148.somn.feature.alarm.service.AlarmService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import dev.vic41148.somn.core.domain.repository.AlarmScheduler

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val alarms = alarmRepository.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isAlarmFiring = AlarmService.isAlarmFiring
    val currentAlarmLabel = AlarmService.currentAlarmLabel
    val currentCaptchaType = AlarmService.currentCaptchaType
    val canSnooze = AlarmService.canSnooze

    private val _editingAlarm = MutableStateFlow<Alarm?>(null)
    val editingAlarm: StateFlow<Alarm?> = _editingAlarm.asStateFlow()

    fun createAlarm(hour: Int, minute: Int, label: String = "", captchaType: String = "math", repeatDays: Set<Int> = emptySet()) {
        viewModelScope.launch {
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                label = label,
                repeatDays = repeatDays,
                captchaType = try {
                    CaptchaType.valueOf(captchaType.uppercase())
                } catch (e: Exception) {
                    CaptchaType.NONE
                }
            )
            val id = alarmRepository.createAlarm(alarm)
            val newAlarm = alarm.copy(id = id)
            alarmScheduler.schedule(newAlarm)
        }
    }

    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmRepository.updateAlarm(alarm)
            alarmScheduler.schedule(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmScheduler.cancel(alarm.id)
            alarmRepository.deleteAlarm(alarm)
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val nextState = !alarm.isEnabled
            alarmRepository.setEnabled(alarm.id, nextState)
            if (nextState) {
                alarmScheduler.schedule(alarm.copy(isEnabled = true))
            } else {
                alarmScheduler.cancel(alarm.id)
            }
        }
    }

    fun setEditingAlarm(alarm: Alarm?) {
        _editingAlarm.value = alarm
    }

    fun loadAlarmForEditing(id: Long) {
        viewModelScope.launch {
            val alarm = alarmRepository.getAlarm(id)
            _editingAlarm.value = alarm
        }
    }

    fun dismissAlarm(context: android.content.Context) {
        AlarmService.dismiss(context)
    }

    fun snoozeAlarm(context: android.content.Context, durationMinutes: Int = 9) {
        AlarmService.snooze(context, durationMinutes)
    }
}
