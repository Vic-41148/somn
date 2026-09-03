package dev.vic41148.somn.core.audio

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes an arbitrary audio asset (mp3, m4a, wav, ogg - whatever the device's codecs support) to
 * mono 16kHz PCM16, which is what both [AudioEventClassifier]/[AudioCollector] and
 * [YamnetAudioClassifier] assume.
 *
 * Exists only for [AudioAccuracyHarnessTest] (AUDIO-02): a labeled corpus is realistically recorded
 * on a phone's own voice-memo app, whatever format that happens to export, not hand-converted to a
 * specific PCM container - MediaCodec's job is normally exactly this kind of format normalization.
 */
object AudioCorpusDecoder {

    const val TARGET_SAMPLE_RATE = 16000

    fun decodeToMono16k(afd: AssetFileDescriptor): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found")

            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = decodeLoop(extractor, codec)
            val mono = if (sourceChannels > 1) downmix(pcm, sourceChannels) else pcm
            return if (sourceSampleRate != TARGET_SAMPLE_RATE) {
                resample(mono, sourceSampleRate, TARGET_SAMPLE_RATE)
            } else {
                mono
            }
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    private fun decodeLoop(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        val out = mutableListOf<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)!!
                    val shorts = ShortArray(info.size / 2)
                    outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    out.addAll(shorts.toList())
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        return out.toShortArray()
    }

    private fun downmix(interleaved: ShortArray, channels: Int): ShortArray {
        val frames = interleaved.size / channels
        return ShortArray(frames) { frame ->
            var sum = 0
            for (c in 0 until channels) sum += interleaved[frame * channels + c]
            (sum / channels).toShort()
        }
    }

    /** Linear interpolation - accurate enough for classification input, not for playback quality. */
    private fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (input.isEmpty() || fromRate == toRate) return input
        val ratio = toRate.toDouble() / fromRate.toDouble()
        val outLength = (input.size * ratio).roundToInt()
        return ShortArray(outLength) { i ->
            val srcPos = i / ratio
            val srcIndex = srcPos.toInt().coerceIn(0, input.size - 1)
            val nextIndex = (srcIndex + 1).coerceIn(0, input.size - 1)
            val frac = srcPos - srcIndex
            (input[srcIndex] + (input[nextIndex] - input[srcIndex]) * frac).roundToInt().toShort()
        }
    }
}
