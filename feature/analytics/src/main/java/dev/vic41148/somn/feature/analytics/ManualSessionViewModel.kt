package dev.vic41148.somn.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.usecase.ManualSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the manual-entry form: turns user-entered bed/wake times into a completed
 * (sensor-less) session via [ManualSessionUseCase] and persists it. Exposes a single
 * [saved] event so the screen can pop itself.
 *
 * Validation lives on the screen (the Save button stays disabled until the times are
 * valid), so the screen only ever calls [save] with valid times. The code still checks the
 * use case's null return defensively - it never silently drops an entry the user confirmed.
 */
@HiltViewModel
class ManualSessionViewModel @Inject constructor(
    private val manualSessionUseCase: ManualSessionUseCase,
    private val sleepRepository: SleepRepository
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(startTimeMillis: Long, endTimeMillis: Long) {
        viewModelScope.launch {
            val session = manualSessionUseCase.createManualSession(startTimeMillis, endTimeMillis)
            if (session == null) {
                android.util.Log.w("ManualSessionViewModel", "Rejected manual entry with invalid times")
            } else {
                sleepRepository.insertManualSession(session)
                _saved.value = true
            }
        }
    }
}
