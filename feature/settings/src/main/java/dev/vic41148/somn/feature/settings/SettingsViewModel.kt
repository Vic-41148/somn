package dev.vic41148.somn.feature.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.domain.model.TrackingMode
import dev.vic41148.somn.core.domain.usecase.ExportCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val exportCsv: ExportCsvUseCase
) : ViewModel() {

    // Settings state
    data class SettingsState(
        val targetSleepHours: Float = 8.0f,
        val wakeWindowMinutes: Int = 30,
        val sensorMode: String = "Accelerometer",
        val dndEnabled: Boolean = true,
        val batteryThreshold: Int = 15,
        val darkMode: String = "System",
        val trackingMode: TrackingMode = TrackingMode.ACCELEROMETER,
        val selectedCaptchaTaskId: String = dev.vic41148.somn.core.domain.model.AlarmPreferences.selectedCaptchaTaskId,
        val qrCodeValue: String? = dev.vic41148.somn.core.domain.model.AlarmPreferences.qrCodeValue
    )

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun updateSleepTarget(hours: Float) {
        _settings.value = _settings.value.copy(targetSleepHours = hours)
    }

    fun updateWakeWindow(minutes: Int) {
        _settings.value = _settings.value.copy(wakeWindowMinutes = minutes)
    }

    fun updateDndEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(dndEnabled = enabled)
    }

    fun updateBatteryThreshold(threshold: Int) {
        _settings.value = _settings.value.copy(batteryThreshold = threshold)
    }

    fun updateTrackingMode(mode: TrackingMode) {
        _settings.value = _settings.value.copy(trackingMode = mode)
    }

    fun updateCaptchaTask(taskId: String) {
        _settings.value = _settings.value.copy(selectedCaptchaTaskId = taskId)
        dev.vic41148.somn.core.domain.model.AlarmPreferences.selectedCaptchaTaskId = taskId
    }

    fun updateQRCodeValue(value: String) {
        _settings.value = _settings.value.copy(qrCodeValue = value)
        dev.vic41148.somn.core.domain.model.AlarmPreferences.qrCodeValue = value
    }

    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                val sessions = sleepRepository.getRecentSessions(1000)
                val csv = exportCsv(sessions)

                val file = File(context.cacheDir, "sleep_data_export.csv")
                file.writeText(csv)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Sleep Data"))

                _exportStatus.value = "Export ready!"
            } catch (e: Exception) {
                _exportStatus.value = "Export failed: ${e.message}"
            }
        }
    }
}
