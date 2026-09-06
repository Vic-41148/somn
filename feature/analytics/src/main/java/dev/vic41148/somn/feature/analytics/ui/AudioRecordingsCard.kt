package dev.vic41148.somn.feature.analytics.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.label
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AudioRecordingsCard"

/** Playback state for one clip at a time. Only one clip ever plays. */
sealed interface ClipPlayback {
    data object Idle : ClipPlayback
    data class Loading(val eventId: Long) : ClipPlayback
    data class Active(
        val eventId: Long,
        val paused: Boolean,
        val positionMs: Int,
        val durationMs: Int
    ) : ClipPlayback
    data class Failed(val eventId: Long, val message: String) : ClipPlayback
}

/**
 * Single MediaPlayer for sleep-clip playback, routed as speech audio with visible
 * loading/playing/error state. [resolveFile] decrypts sealed clips to a temp copy.
 */
class AudioClipPlayer(
    private val scope: CoroutineScope,
    private val resolveFile: suspend (String) -> File
) {
    var state by mutableStateOf<ClipPlayback>(ClipPlayback.Idle)
        private set

    private val mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }
    private var tempFile: File? = null

    fun play(event: AudioEvent) {
        val path = event.clipPath ?: return
        scope.launch {
            stopInternal()
            state = ClipPlayback.Loading(event.id)
            try {
                val file = withContext(Dispatchers.IO) { resolveFile(path) }
                if (!file.exists()) {
                    state = ClipPlayback.Failed(event.id, "Recording file is missing.")
                    return@launch
                }
                if (file.name.startsWith("play_")) tempFile = file
                mediaPlayer.reset()
                mediaPlayer.setOnCompletionListener {
                    clearTemp()
                    state = ClipPlayback.Idle
                }
                mediaPlayer.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Clip playback failed: what=$what extra=$extra")
                    clearTemp()
                    state = ClipPlayback.Failed(event.id, "Could not play this recording.")
                    true
                }
                mediaPlayer.setDataSource(file.path)
                mediaPlayer.setOnPreparedListener {
                    it.start()
                    state = ClipPlayback.Active(
                        eventId = event.id,
                        paused = false,
                        positionMs = 0,
                        durationMs = it.duration.coerceAtLeast(0)
                    )
                }
                mediaPlayer.prepareAsync()
            } catch (e: Exception) {
                // No throwable and no path: setDataSource embeds the clip path in the
                // exception, and Log.e survives release stripping.
                Log.e(TAG, "Failed to play clip (${e.javaClass.simpleName})")
                state = ClipPlayback.Failed(event.id, "Could not play this recording.")
            }
        }
    }

    fun toggle(event: AudioEvent) {
        when (val current = state) {
            is ClipPlayback.Active if current.eventId == event.id -> {
                if (current.paused) {
                    mediaPlayer.start()
                    state = current.copy(paused = false)
                } else {
                    mediaPlayer.pause()
                    state = current.copy(paused = true, positionMs = mediaPlayer.currentPosition)
                }
            }
            else -> play(event)
        }
    }

    fun stop() {
        scope.launch { stopInternal() }
        state = ClipPlayback.Idle
    }

    /** Polls progress while playing. Called from a LaunchedEffect loop. */
    fun refresh() {
        val current = state
        if (current is ClipPlayback.Active && !current.paused) {
            state = try {
                current.copy(positionMs = mediaPlayer.currentPosition)
            } catch (_: IllegalStateException) {
                ClipPlayback.Idle
            }
        }
    }

    fun release() {
        runCatching { mediaPlayer.release() }
        clearTemp()
    }

    private fun stopInternal() {
        runCatching {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
        }
        clearTemp()
    }

    private fun clearTemp() {
        tempFile?.takeIf { it.name.startsWith("play_") }?.delete()
        tempFile = null
    }
}

@Composable
fun rememberAudioClipPlayer(resolveFile: suspend (String) -> File): AudioClipPlayer {
    val scope = rememberCoroutineScope()
    val player = remember { AudioClipPlayer(scope, resolveFile) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Standard recording list: every event that kept a clip (talk, snore, cough), with
 * play/pause, progress, stop, and a visible error when a clip will not play.
 */
@Composable
fun AudioRecordingsCard(
    events: List<AudioEvent>,
    player: AudioClipPlayer,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    selectedEventId: Long? = null
) {
    if (events.isEmpty()) return
    val playback = player.state

    if (playback is ClipPlayback.Active && !playback.paused) {
        LaunchedEffect(playback.eventId) {
            while (true) {
                delay(250)
                player.refresh()
            }
        }
    }

    SleepCard(title = "Recordings", modifier = modifier) {
        events.forEachIndexed { index, event ->
            val active = (playback as? ClipPlayback.Active)?.takeIf { it.eventId == event.id }
            val failed = (playback as? ClipPlayback.Failed)?.takeIf { it.eventId == event.id }
            val loading = (playback as? ClipPlayback.Loading)?.eventId == event.id
            Column {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "${event.type.label()} at ${formatTime(event.timestampMillis)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = when {
                                loading -> "Loading…"
                                active != null ->
                                    "${active.positionMs / 1000}s of ${active.durationMs / 1000}s"
                                else -> "${event.durationSeconds}s · ${event.intensityDecibels} dB"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (active != null) {
                                TextButton(onClick = { player.stop() }) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                                }
                            }
                            IconButton(onClick = { player.toggle(event) }) {
                                Icon(
                                    if (active != null && !active.paused) Icons.Default.Pause
                                    else Icons.Default.PlayArrow,
                                    contentDescription = if (active != null && !active.paused) "Pause"
                                    else "Play ${event.type.label().lowercase()} clip"
                                )
                            }
                        }
                    },
                    tonalElevation = if (event.id == selectedEventId) 2.dp else 0.dp
                )
                if (active != null && active.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (active.positionMs.toFloat() / active.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (failed != null) {
                    Text(
                        text = failed.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            if (index < events.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
