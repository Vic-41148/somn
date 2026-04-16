package dev.vic41148.somn.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import dev.vic41148.somn.core.audio.sensor.AccelerometerCollector
import org.jtransforms.fft.FloatFFT_1D
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ultrasonic Doppler sonar for contactless sleep movement tracking.
 *
 * Emits a continuous 19kHz tone through the speaker and simultaneously records
 * via the microphone at 44100Hz. Movement in the room causes Doppler shifts and
 * amplitude modulation in the 19kHz band, which are extracted via FFT and
 * converted into [SonarEpochData] (compatible format with AccelerometerCollector.EpochData).
 *
 * Architecture:
 *   - AudioTrack and AudioRecord run on dedicated Java Threads to avoid
 *     coroutine dispatcher scheduling jitter that would degrade FFT accuracy.
 *   - A 60-second calibration window establishes a baseline amplitude at 19kHz.
 *     Null epochs are emitted during calibration — callers must handle null gracefully.
 *   - After calibration, each 30-second window is FFT-analysed and movement
 *     magnitude = deviation of 19kHz-band peak from baseline.
 *   - If SNR drops below threshold for 3 consecutive epochs, [fallbackRequired] fires.
 *
 * Known limitation: pets, partners, and intermittent sources will inflate magnitude.
 * Stable broadband noise (fans, AC) is naturally suppressed by baseline subtraction.
 */
@SuppressLint("MissingPermission")
class SonarCollector(private val context: Context) {

    companion object {
        private const val EMITTER_FREQ_HZ   = 19_000f
        private const val SAMPLE_RATE        = 44_100
        private const val BAND_LOW_HZ        = 18_500f
        private const val BAND_HIGH_HZ       = 19_500f
        private const val EPOCH_MS           = 30_000L
        private const val CALIBRATION_MS     = 60_000L
        private const val SNR_THRESHOLD      = 0.2f
        private const val SNR_FALLBACK_EPOCHS = 3
        const val EVENT_TAG_FALLBACK          = "TrackingModeSwitch"
    }

    /** Emits null during calibration, [SonarEpochData] after calibration completes. */
    private val _epochChannel = Channel<SonarEpochData?>(Channel.BUFFERED)
    val epochFlow: Flow<SonarEpochData?> = _epochChannel.receiveAsFlow()

    private val _calibrationChannel = Channel<SonarCalibrationState>(Channel.CONFLATED)
    val calibrationFlow: Flow<SonarCalibrationState> = _calibrationChannel.receiveAsFlow()

    /** Fires once when SNR has been poor for [SNR_FALLBACK_EPOCHS] consecutive epochs. */
    private val _fallbackChannel = Channel<Unit>(Channel.CONFLATED)
    val fallbackRequired: Flow<Unit> = _fallbackChannel.receiveAsFlow()

    @Volatile private var running = false
    private var emitterThread: Thread? = null
    private var recordThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null

    @Volatile private var baselineAmplitude  = 0f
    @Volatile private var isCalibrating      = true
    private val calibrationSamples           = mutableListOf<Float>()
    @Volatile private var sessionStartMs     = 0L

    private val epochBuffer  = mutableListOf<Float>()
    private val epochLock    = Any()
    @Volatile private var epochWindowStart = 0L

    private var consecutiveLowSnrEpochs = 0

    // ─── Public API ────────────────────────────────────────────────────────────

    fun start() {
        if (running) return
        running          = true
        sessionStartMs   = System.currentTimeMillis()
        isCalibrating    = true
        baselineAmplitude = 0f
        calibrationSamples.clear()
        consecutiveLowSnrEpochs = 0
        epochWindowStart = System.currentTimeMillis()
        _calibrationChannel.trySend(SonarCalibrationState.CALIBRATING)
        startEmitter()
        startRecorder()
    }

    fun stop() {
        running = false
        emitterThread?.interrupt()
        recordThread?.interrupt()
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioTrack?.release()
        audioRecord?.release()
        audioTrack  = null
        audioRecord = null
        _calibrationChannel.trySend(SonarCalibrationState.IDLE)
    }

    // ─── Emitter thread ────────────────────────────────────────────────────────

    private fun startEmitter() {
        val minBuf  = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bufSize = maxOf(minBuf, SAMPLE_RATE)
        val tone    = generateToneBuffer(EMITTER_FREQ_HZ, SAMPLE_RATE, bufSize)

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufSize * 4,
            AudioTrack.MODE_STREAM
        )

        emitterThread = Thread({
            audioTrack?.play()
            while (running && !Thread.currentThread().isInterrupted) {
                audioTrack?.write(tone, 0, tone.size, AudioTrack.WRITE_BLOCKING)
            }
        }, "SonarEmitter").also { it.start() }
    }

    // ─── Recorder thread ───────────────────────────────────────────────────────

    private fun startRecorder() {
        val minBuf  = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bufSize = maxOf(minBuf, SAMPLE_RATE)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufSize * 4
        )

        recordThread = Thread({
            audioRecord?.startRecording()
            val localBuf = FloatArray(bufSize)

            while (running && !Thread.currentThread().isInterrupted) {
                val read = audioRecord?.read(
                    localBuf, 0, localBuf.size, AudioRecord.READ_BLOCKING) ?: -1
                if (read <= 0) continue

                val chunk    = localBuf.copyOf(read)
                val bandPeak = extractBandPeak(chunk)
                val nowMs    = System.currentTimeMillis()
                val elapsed  = nowMs - sessionStartMs

                if (elapsed < CALIBRATION_MS) {
                    // Calibration window — accumulate baseline, emit null epoch
                    calibrationSamples.add(bandPeak)
                    _epochChannel.trySend(null)
                } else {
                    // Transition to active on first post-calibration chunk
                    if (isCalibrating) {
                        baselineAmplitude = if (calibrationSamples.isNotEmpty())
                            calibrationSamples.average().toFloat() else 1f
                        isCalibrating = false
                        _calibrationChannel.trySend(SonarCalibrationState.READY)
                        android.util.Log.i("SonarCollector",
                            "Calibration done — baseline=${baselineAmplitude}")
                    }

                    synchronized(epochLock) {
                        epochBuffer.addAll(chunk.toList())
                        if (nowMs - epochWindowStart >= EPOCH_MS) {
                            processEpoch(nowMs)
                            epochWindowStart = nowMs
                        }
                    }
                }
            }
        }, "SonarRecorder").also { it.start() }
    }

    // ─── Signal processing ─────────────────────────────────────────────────────

    private fun processEpoch(nowMs: Long) {
        val samples = synchronized(epochLock) {
            val copy = epochBuffer.toFloatArray()
            epochBuffer.clear()
            copy
        }
        if (samples.isEmpty()) return

        // Compute 1-second chunk peaks, then aggregate over epoch
        val chunkSize = SAMPLE_RATE
        val peaks     = mutableListOf<Float>()
        var offset    = 0
        while (offset + chunkSize <= samples.size) {
            peaks.add(extractBandPeak(samples.copyOfRange(offset, offset + chunkSize)))
            offset += chunkSize
        }
        if (peaks.isEmpty()) return

        val meanPeak  = peaks.average().toFloat()
        val deviation = abs(meanPeak - baselineAmplitude)

        // SNR check — if mean peak is far below baseline the mic/speaker path is broken
        val snr = if (baselineAmplitude > 0f) meanPeak / baselineAmplitude else 0f
        if (snr < SNR_THRESHOLD) {
            consecutiveLowSnrEpochs++
            android.util.Log.w("SonarCollector",
                "Low SNR (${"%.3f".format(snr)}) epoch $consecutiveLowSnrEpochs/$SNR_FALLBACK_EPOCHS")
            if (consecutiveLowSnrEpochs >= SNR_FALLBACK_EPOCHS) {
                android.util.Log.e("SonarCollector",
                    "$EVENT_TAG_FALLBACK: requesting accelerometer fallback")
                _fallbackChannel.trySend(Unit)
                consecutiveLowSnrEpochs = 0
            }
        } else {
            consecutiveLowSnrEpochs = 0
        }

        // Variability = std-dev of per-second peaks within this epoch window
        val mean     = peaks.average().toFloat()
        val variance = peaks.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev   = sqrt(variance.toDouble()).toFloat()

        _epochChannel.trySend(
            SonarEpochData(
                timestampMillis     = nowMs - EPOCH_MS,
                movementMagnitude   = deviation,
                movementVariability = stdDev
            )
        )
    }

    /** Returns the peak amplitude in the [BAND_LOW_HZ]–[BAND_HIGH_HZ] band for one chunk. */
    private fun extractBandPeak(chunk: FloatArray): Float {
        val n       = nextPow2(chunk.size)
        // JTransforms complex FFT requires interleaved re/im pairs
        val fftData = FloatArray(n * 2)
        for (i in chunk.indices) {
            fftData[i * 2]     = chunk[i]
            fftData[i * 2 + 1] = 0f
        }
        FloatFFT_1D(n.toLong()).complexForward(fftData)

        val freqRes = SAMPLE_RATE.toFloat() / n
        val binLow  = (BAND_LOW_HZ  / freqRes).toInt().coerceIn(0, n - 1)
        val binHigh = (BAND_HIGH_HZ / freqRes).toInt().coerceIn(0, n - 1)

        var peak = 0f
        for (bin in binLow..binHigh) {
            val re  = fftData[bin * 2]
            val im  = fftData[bin * 2 + 1]
            val amp = sqrt((re * re + im * im).toDouble()).toFloat()
            if (amp > peak) peak = amp
        }
        return peak
    }

    private fun generateToneBuffer(freqHz: Float, sampleRate: Int, samples: Int): FloatArray {
        val buf         = FloatArray(samples)
        val angularFreq = 2.0 * Math.PI * freqHz / sampleRate
        for (i in buf.indices) buf[i] = Math.sin(angularFreq * i).toFloat()
        return buf
    }

    private fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    // ─── Data types ────────────────────────────────────────────────────────────

    /**
     * Sonar-derived movement data, structurally identical to AccelerometerCollector.EpochData.
     * SleepTrackingService adapts this into AccelerometerCollector.EpochData for the
     * [ClassifySleepStageUseCase] pipeline.
     */
    data class SonarEpochData(
        val timestampMillis:     Long,
        val movementMagnitude:   Float,
        val movementVariability: Float
    )

    enum class SonarCalibrationState { IDLE, CALIBRATING, READY }
}
