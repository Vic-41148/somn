package dev.vic41148.somn.feature.analytics.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.feature.analytics.ManualSessionViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSessionScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ManualSessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Default to the most common use case - adding last night's sleep the following morning:
    // woke today (or yesterday, if it is before 7am) at 07:00, went to bed the night before at
    // 23:00. Both are just starting points. The pickers adjust freely.
    val now = LocalDateTime.now()
    var defaultWake = LocalDateTime.of(now.toLocalDate(), LocalTime.of(7, 0))
    if (defaultWake.isAfter(now)) defaultWake = defaultWake.minusDays(1)
    val defaultBed = defaultWake.minusDays(1).withHour(23).withMinute(0)

    var bedMillis by rememberSaveable { mutableLongStateOf(toMillis(defaultBed)) }
    var wakeMillis by rememberSaveable { mutableLongStateOf(toMillis(defaultWake)) }

    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    val durationMinutes = ((wakeMillis - bedMillis) / 60_000).toInt()
    // A manual entry is a past night. Wake must not be in the future. A >24h span is a
    // typo (or a misunderstanding of what counts as one night), not a valid session.
    val isValid = durationMinutes >= 15 && durationMinutes <= 1440 &&
        wakeMillis <= System.currentTimeMillis()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Add Manual Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add a missed night",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You forgot to start tracking. Enter the best times you remember. " +
                    "The app stores only the bed and wake times. It has no sensor data. " +
                    "It estimates the score from duration alone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Bed time",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            DateTimeField(
                valueMillis = bedMillis,
                onPicked = { bedMillis = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Wake time",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            DateTimeField(
                valueMillis = wakeMillis,
                onPicked = { wakeMillis = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when {
                    isValid -> "Duration: ${durationMinutes / 60}h ${durationMinutes % 60}m"
                    wakeMillis > System.currentTimeMillis() ->
                        "Wake time cannot be in the future."
                    durationMinutes < 15 ->
                        "Wake time must be at least 15 minutes after the bed time."
                    else ->
                        "A manual night cannot be longer than 24 hours."
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isValid) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.save(bedMillis, wakeMillis) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Add Session")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The night appears in History and Trends like any other session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A full-width button showing the current value. Tapping it opens the date picker then the time picker. */
@Composable
private fun DateTimeField(
    valueMillis: Long,
    onPicked: (Long) -> Unit
) {
    val context = LocalContext.current

    OutlinedButton(
        onClick = {
            val calendar = Calendar.getInstance().apply { timeInMillis = valueMillis }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    // After the user confirms the date, chain straight into the time picker.
                    // The user then enters the whole value in two taps instead of a picker hunt.
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onPicked(
                                LocalDateTime.of(year, month + 1, day, hour, minute)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            )
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // A manual entry is a past night - never let the picker land on a future date.
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            text = Instant.ofEpochMilli(valueMillis)
                .atZone(ZoneId.systemDefault())
                .format(dateTimeFormatter),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun toMillis(dateTime: LocalDateTime): Long =
    dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
