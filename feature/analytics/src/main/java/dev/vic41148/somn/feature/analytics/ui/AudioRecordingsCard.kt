package dev.vic41148.somn.feature.analytics.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.vic41148.somn.core.domain.model.AudioEvent
import dev.vic41148.somn.core.domain.model.AudioEventType
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.label
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AudioRecordingsCard"

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)

/** Playback state for one clip at a time. Only one clip ever plays. */
sealed interface ClipPlayback {
    data object Idle : ClipPlayback
    data class Loading(val eventId: Long) : ClipPlayback
    data class Active(
        val eventId: Long,
        val paused: Boolean,
        val positionMs: Int,
        val durationMs: Int,
        val speed: Float = 1f
    ) : ClipPlayback
    data class Failed(val eventId: Long, val message: String) : ClipPlayback
}

/**
 * Single MediaPlayer for sleep-clip playback, routed as speech audio with visible
 * loading/playing/error state. [resolveFile] decrypts sealed clips to a temp copy.
 * Owns the queue so tracks advance, skip, and scrub like a music player.
 */
class AudioClipPlayer(
    private val scope: CoroutineScope,
    private val resolveFile: suspend (String) -> File
) {
    var state by mutableStateOf<ClipPlayback>(ClipPlayback.Idle)
        private set

    /** The playlist the current clip belongs to; rows keep it pointed at the list. */
    var queue: List<AudioEvent> = emptyList()

    var speed: Float = 1f
        private set

    val currentIndex: Int
        get() {
            val id = when (val s = state) {
                is ClipPlayback.Active -> s.eventId
                is ClipPlayback.Loading -> s.eventId
                is ClipPlayback.Failed -> s.eventId
                else -> return -1
            }
            return queue.indexOfFirst { it.id == id }
        }

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
        if ((state as? ClipPlayback.Loading)?.eventId == event.id) return
        scope.launch {
            stopInternal()
            state = ClipPlayback.Loading(event.id)
            try {
                val file = withContext(Dispatchers.IO) { resolveFile(event.clipPath ?: "") }
                if (!file.exists()) {
                    state = ClipPlayback.Failed(event.id, "Recording file is missing.")
                    return@launch
                }
                if (file.name.startsWith("play_")) tempFile = file
                mediaPlayer.reset()
                mediaPlayer.setOnCompletionListener {
                    val next = queue.getOrNull(currentIndex + 1)
                    clearTemp()
                    if (next != null) play(next) else state = ClipPlayback.Idle
                }
                mediaPlayer.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Clip playback failed: what=$what extra=$extra")
                    clearTemp()
                    state = ClipPlayback.Failed(event.id, "Could not play this recording.")
                    true
                }
                mediaPlayer.setDataSource(file.path)
                mediaPlayer.setOnPreparedListener {
                    applySpeed()
                    it.start()
                    state = ClipPlayback.Active(
                        eventId = event.id,
                        paused = false,
                        positionMs = 0,
                        durationMs = it.duration.coerceAtLeast(0),
                        speed = speed
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

    /** Next clip, if there is one. */
    fun next() {
        val i = currentIndex
        if (i >= 0 && i < queue.lastIndex) play(queue[i + 1])
    }

    /**
     * Restarts the clip past 3s in (music-player behavior), otherwise the
     * previous clip.
     */
    fun previous() {
        val current = state as? ClipPlayback.Active
        if (current != null && current.positionMs > 3000) {
            seekTo(0)
            return
        }
        val i = currentIndex
        if (i > 0) play(queue[i - 1])
    }

    fun seekTo(ms: Int) {
        val current = state
        if (current is ClipPlayback.Active) {
            runCatching { mediaPlayer.seekTo(ms) }
            state = current.copy(positionMs = ms.coerceIn(0, current.durationMs))
        }
    }

    fun cycleSpeed() {
        speed = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size]
        applySpeed()
        (state as? ClipPlayback.Active)?.let { state = it.copy(speed = speed) }
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

    private fun applySpeed() {
        runCatching {
            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
        }
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

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "${totalSec / 60}:${"%02d".format(totalSec % 60)}"
}

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "x"

private fun typeIcon(type: AudioEventType) = when (type) {
    AudioEventType.TALK -> Icons.Default.RecordVoiceOver
    AudioEventType.SNORE -> Icons.Default.Bedtime
    AudioEventType.COUGH -> Icons.Default.Air
    AudioEventType.ANOMALY -> Icons.Default.Warning
}

/**
 * Now-playing deck for the current clip: artwork medallion, track position,
 * scrubbable progress, transport controls, speed, and close.
 */
@Composable
private fun NowPlayingCard(
    player: AudioClipPlayer,
    event: AudioEvent,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val playback = player.state
    val index = player.currentIndex
    SleepCard(title = "Now Playing", modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = typeIcon(event.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${event.type.label()} at ${formatTime(event.timestampMillis)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (index >= 0) "Track ${index + 1} of ${player.queue.size}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { player.stop() }) {
                Icon(Icons.Default.Close, contentDescription = "Close player")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (playback) {
            is ClipPlayback.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ClipPlayback.Failed -> {
                Text(
                    text = playback.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { player.play(event) }) { Text("Retry") }
            }
            is ClipPlayback.Active -> {
                var scrubbing by remember(event.id) { mutableStateOf(false) }
                var scrubValue by remember(event.id) {
                    mutableFloatStateOf(playback.positionMs.toFloat())
                }
                Slider(
                    value = if (scrubbing) scrubValue else playback.positionMs.toFloat(),
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        player.seekTo(scrubValue.toInt())
                        scrubbing = false
                    },
                    valueRange = 0f..playback.durationMs.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(if (scrubbing) scrubValue.toInt() else playback.positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMs(playback.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous clip")
                    }
                    FilledTonalIconButton(
                        onClick = { player.toggle(event) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (playback.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (playback.paused) "Play" else "Pause",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(
                        onClick = { player.next() },
                        enabled = index in 0 until player.queue.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next clip")
                    }
                    TextButton(onClick = { player.cycleSpeed() }) {
                        Text(speedLabel(playback.speed), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            ClipPlayback.Idle -> Unit
        }
    }
}

/**
 * Recordings section: a now-playing deck for the current clip over the full
 * clip list. Tapping a row loads it into the deck and plays.
 */
@Composable
fun AudioRecordingsCard(
    events: List<AudioEvent>,
    player: AudioClipPlayer,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    selectedEventId: Long? = null,
    onShareClick: (AudioEvent) -> Unit = {}
) {
    if (events.isEmpty()) return
    player.queue = events
    val playback = player.state
    var listExpanded by remember { mutableStateOf(false) }

    if (playback is ClipPlayback.Active && !playback.paused) {
        LaunchedEffect(playback.eventId) {
            while (true) {
                delay(250)
                player.refresh()
            }
        }
    }

    val nowPlayingId = when (playback) {
        is ClipPlayback.Active -> playback.eventId
        is ClipPlayback.Loading -> playback.eventId
        is ClipPlayback.Failed -> playback.eventId
        ClipPlayback.Idle -> null
    }
    events.find { it.id == nowPlayingId }?.let { event ->
        NowPlayingCard(
            player = player,
            event = event,
            formatTime = formatTime,
            modifier = modifier
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    SleepCard(
        title = "Recordings (${events.size})",
        modifier = modifier,
        action = {
            IconButton(onClick = { listExpanded = !listExpanded }) {
                Icon(
                    if (listExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (listExpanded) "Collapse recordings" else "Expand recordings"
                )
            }
        }
    ) {
        AnimatedVisibility(
            visible = listExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(durationMillis = 100))
        ) {
            Column {
                events.forEachIndexed { index, event ->
                    val activeId = (playback as? ClipPlayback.Active)?.eventId
                    val isCurrent = event.id == nowPlayingId
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
                                    text = "${event.durationSeconds}s · ${event.intensityDecibels} dB",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = typeIcon(event.type),
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onShareClick(event) }) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Export ${event.type.label().lowercase()} clip"
                                        )
                                    }
                                    IconButton(onClick = { player.toggle(event) }) {
                                        Icon(
                                            if (activeId == event.id &&
                                                (playback as? ClipPlayback.Active)?.paused == false
                                            ) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play ${event.type.label().lowercase()} clip"
                                        )
                                    }
                                }
                            },
                            tonalElevation = if (event.id == selectedEventId || isCurrent) 2.dp else 0.dp
                        )
                    }
                    if (index < events.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
