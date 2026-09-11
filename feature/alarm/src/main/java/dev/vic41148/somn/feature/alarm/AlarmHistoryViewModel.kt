package dev.vic41148.somn.feature.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vic41148.somn.core.data.repository.AlarmEventRepository
import dev.vic41148.somn.core.domain.model.AlarmEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class AlarmHistoryViewModel @Inject constructor(
    private val alarmEventRepository: AlarmEventRepository
) : ViewModel() {

    val events: StateFlow<List<AlarmEvent>> = alarmEventRepository.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Missed alarms over the trailing 7-day window, for the summary header. */
    val missedThisWeek: StateFlow<Int> = alarmEventRepository.observeMissedSince(weekStartMillis())
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun clearHistory() {
        viewModelScope.launch {
            alarmEventRepository.clearHistory()
        }
    }

    private fun weekStartMillis(): Long =
        LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}