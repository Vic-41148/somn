package dev.vic41148.somn.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

/**
 * Handles recording audio buffers.
 * Requires android.permission.RECORD_AUDIO
 */
class AudioCollector(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val _audioFlow = MutableSharedFlow<ShortArray>(extraBufferCapacity = 10)
    val audioFlow: Flow<ShortArray> = _audioFlow.asSharedFlow()

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2) // 1 second buffer min

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecording = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    suspend fun collectLoop() {
        if (!isRecording) return
        val buffer = ShortArray(SAMPLE_RATE) // 1 second of audio
        while (isRecording && kotlinx.coroutines.currentCoroutineContext().isActive) {
            val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readResult > 0) {
                // Emit copy of buffer
                _audioFlow.tryEmit(buffer.copyOf(readResult))
            }
            yield()
        }
    }

    fun stop() {
        if (isRecording) {
            isRecording = false
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            audioRecord = null
        }
    }
}
