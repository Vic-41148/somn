package dev.vic41148.somn.feature.alarm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vic41148.somn.core.data.repository.AlarmRepository
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.domain.model.Alarm
import dev.vic41148.somn.core.domain.model.CaptchaType
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTask
import dev.vic41148.somn.feature.alarm.captcha.CaptchaTaskRegistry
import dev.vic41148.somn.feature.alarm.service.AlarmService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import dev.vic41148.somn.core.domain.repository.AlarmScheduler

@HiltViewModel
class AlarmViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val preferencesRepository: SomnPreferencesRepository
) : ViewModel() {

    val alarms = alarmRepository.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isAlarmFiring = AlarmService.isAlarmFiring
    val currentAlarmLabel = AlarmService.currentAlarmLabel
    val currentCaptchaType = AlarmService.currentCaptchaType
    val canSnooze = AlarmService.canSnooze

    /**
     * The captcha task gating the current firing episode, resolved for the in-app firing screen.
     * Resolved (fresh, reset) on every firing start — first ring and each WAKE-02 re-ring — using
     * the same [CaptchaTaskRegistry.resolveTask] precedence as [AlarmActivity].
     *
     * Keyed on [AlarmService.isAlarmFiring] rather than `phase`: the phase StateFlow initializes
     * to FIRING, so keying on it would spuriously resolve a task in every ViewModel instance and
     * would miss the first real fire (the value never re-emits). `isAlarmFiring` starts false,
     * flips true on every real fire and re-ring, and goes false during the wake-confirm window.
     */
    private val _captchaTask = MutableStateFlow<CaptchaTask?>(null)
    val captchaTask: StateFlow<CaptchaTask?> = _captchaTask.asStateFlow()

    /**
     * True once the first resolution has completed. The firing screen keeps Dismiss locked on a
     * blank surface until this is true — without the gate, the task starts null and the shared
     * [dev.vic41148.somn.feature.alarm.ui.AlarmScreen] treats a null task as "Unlocked!", which
     * would open a small captcha-bypass window while the DataStore reads are in flight.
     */
    private val _captchaReady = MutableStateFlow(false)
    val captchaReady: StateFlow<Boolean> = _captchaReady.asStateFlow()

    init {
        viewModelScope.launch {
            AlarmService.isAlarmFiring.collect { firing ->
                if (firing) {
                    val perAlarmType = AlarmService.currentCaptchaType.value
                    val globalTaskId = preferencesRepository.selectedCaptchaTaskId.first()
                    val qrValue = preferencesRepository.qrCodeValue.first()
                    val nfcAvailable = context.packageManager.hasSystemFeature(
                        android.content.pm.PackageManager.FEATURE_NFC
                    )
                    _captchaTask.value = CaptchaTaskRegistry.resolveTask(
                        perAlarmType, globalTaskId, qrValue, nfcAvailable
                    )
                    _captchaReady.value = true
                }
            }
        }
    }

    private val _editingAlarm = MutableStateFlow<Alarm?>(null)
    val editingAlarm: StateFlow<Alarm?> = _editingAlarm.asStateFlow()

    fun createAlarm(
        hour: Int,
        minute: Int,
        label: String = "",
        // Defaults to NONE so a new alarm inherits the global Settings captcha preference.
        // (Previously "math" — but the per-alarm value was decorative, so it never mattered. Now
        // that AlarmActivity actually honors per-alarm captchaType, a hardcoded "math" default
        // would silently override the user's global choice for every UI-created alarm.)
        captchaType: String = "none",
        repeatDays: Set<Int> = emptySet(),
        // Was missing entirely, so a new alarm always took Alarm's 30-minute default and silently
        // discarded whatever the user had just set on the edit screen's wake-window slider. Only
        // editing an existing alarm ever persisted the value.
        wakeWindowMinutes: Int = 30
    ) {
        viewModelScope.launch {
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                label = label,
                repeatDays = repeatDays,
                wakeWindowMinutes = wakeWindowMinutes,
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

    fun snoozeAlarm(context: android.content.Context, durationMinutes: Int = 9) {
        AlarmService.snooze(context, durationMinutes)
    }
}
