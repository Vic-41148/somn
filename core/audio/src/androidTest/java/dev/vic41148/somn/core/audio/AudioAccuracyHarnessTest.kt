package dev.vic41148.somn.core.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vic41148.somn.core.domain.model.AudioEventType
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AUDIO-02: validates YAMNet's classification accuracy for snore/cough/talk against the existing
 * ZCR heuristic, on a labeled corpus, before YAMNet can replace the heuristic in production.
 *
 * Corpus layout - real recordings, any format the device's codecs can decode (mp3/m4a/wav/ogg),
 * placed at:
 *
 *     core/audio/src/androidTest/assets/audio_corpus/<label>/ (one or more audio files, any extension)
 *
 * where `<label>` is one of `snore`, `cough`, `talk`, `silence`. Each file's *whole* content is
 * treated as one example of its folder's label - trim clips so they don't start with several
 * seconds of something else. 5-15 seconds per clip, a handful per label, is enough to see whether
 * the mapping is directionally sound.
 *
 * No assertions: this prints a confusion matrix and per-classifier accuracy to logcat rather than
 * failing, because a hard pass/fail threshold before any real corpus exists would be arbitrary.
 * Read the result with:
 *   adb logcat -d -s AudioAccuracyHarness:I
 *
 * If `audio_corpus/` is empty or missing, this reports that plainly and does nothing else - it is
 * not a substitute for having real recordings.
 */
@RunWith(AndroidJUnit4::class)
class AudioAccuracyHarnessTest {

    private companion object {
        const val TAG = "AudioAccuracyHarness"
        const val CORPUS_ROOT = "audio_corpus"
        val LABELS = listOf("snore", "cough", "talk", "silence")

        /** One second, matching what AudioCollector actually feeds AudioEventClassifier in production. */
        const val ZCR_BUFFER_SIZE = AudioCollector.SAMPLE_RATE
    }

    private data class Prediction(val label: String, val yamnet: AudioEventType?, val zcr: AudioEventType?)

    @Test
    fun compareYamnetAgainstZcrHeuristicOnLabeledCorpus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = context.assets

        val corpusLabels = runCatching { assets.list(CORPUS_ROOT)?.toList() ?: emptyList() }
            .getOrElse { emptyList() }
            .filter { it in LABELS }

        if (corpusLabels.isEmpty()) {
            android.util.Log.w(
                TAG,
                "No corpus found under assets/$CORPUS_ROOT/{${LABELS.joinToString(",")}} - " +
                    "nothing to validate. This is not a pass; AUDIO-02 needs real clips added there."
            )
            return
        }

        val yamnetClassifier = YamnetAudioClassifier(YamnetTestData.modelFile(context))
        val predictions = mutableListOf<Prediction>()

        try {
            for (label in corpusLabels) {
                val files = assets.list("$CORPUS_ROOT/$label")?.toList().orEmpty()
                if (files.isEmpty()) {
                    android.util.Log.w(TAG, "Label '$label' has no files, skipping")
                    continue
                }
                for (file in files) {
                    val path = "$CORPUS_ROOT/$label/$file"
                    val pcm = try {
                        assets.openFd(path).use { AudioCorpusDecoder.decodeToMono16k(it) }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to decode $path, skipping", e)
                        continue
                    }
                    if (pcm.isEmpty()) {
                        android.util.Log.w(TAG, "$path decoded to zero samples, skipping")
                        continue
                    }

                    val yamnetPrediction = predictWithYamnet(pcm, yamnetClassifier)
                    val zcrPrediction = predictWithZcr(pcm)
                    predictions += Prediction(label, yamnetPrediction, zcrPrediction)
                    android.util.Log.i(
                        TAG,
                        "$path -> yamnet=$yamnetPrediction zcr=$zcrPrediction (truth=$label)"
                    )
                }
            }
        } finally {
            yamnetClassifier.close()
        }

        if (predictions.isEmpty()) {
            android.util.Log.w(TAG, "Corpus folders existed but every file failed to decode - no results")
            return
        }

        logReport("YAMNet", predictions) { it.yamnet }
        logReport("ZCR heuristic", predictions) { it.zcr }
    }

    /** Majority vote across YAMNet's per-window predictions for the whole clip; null if none mapped. */
    private fun predictWithYamnet(pcm: ShortArray, classifier: YamnetAudioClassifier): AudioEventType? {
        val windowSize = YamnetAudioClassifier.WINDOW_SAMPLE_COUNT
        val votes = mutableListOf<AudioEventType>()
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + windowSize, pcm.size)
            classifier.classify(pcm.copyOfRange(offset, end))?.let { votes += it }
            offset += windowSize
        }
        return votes.mode()
    }

    /**
     * Feeds the clip through a fresh [AudioEventClassifier] in 1-second buffers - the same size
     * [AudioCollector] uses live - then appends one silent buffer so a clip that stays loud right to
     * the end still closes out its final event (the classifier only emits on a loud-to-quiet
     * transition; without this, a clip with no trailing silence would never report anything).
     */
    private fun predictWithZcr(pcm: ShortArray): AudioEventType? {
        val classifier = AudioEventClassifier()
        val votes = mutableListOf<AudioEventType>()
        var offset = 0
        var timestamp = 0L
        while (offset < pcm.size) {
            val end = minOf(offset + ZCR_BUFFER_SIZE, pcm.size)
            classifier.processBuffer(pcm.copyOfRange(offset, end), sessionId = 0, timestampMillis = timestamp)
                ?.let { (event, _) -> votes += event.type }
            offset += ZCR_BUFFER_SIZE
            timestamp += 1000L
        }
        classifier.processBuffer(ShortArray(ZCR_BUFFER_SIZE), sessionId = 0, timestampMillis = timestamp)
            ?.let { (event, _) -> votes += event.type }
        return votes.mode()
    }

    private fun List<AudioEventType>.mode(): AudioEventType? =
        groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    private fun logReport(name: String, predictions: List<Prediction>, pick: (Prediction) -> AudioEventType?) {
        val correct = predictions.count { matches(it.label, pick(it)) }
        android.util.Log.i(
            TAG,
            "=== $name: ${correct}/${predictions.size} correct (${(100.0 * correct / predictions.size).roundTo1()}%) ==="
        )

        val byLabel = predictions.groupBy { it.label }
        for ((label, group) in byLabel) {
            val breakdown = group.groupingBy { pick(it)?.name ?: "none" }.eachCount()
                .entries.joinToString(", ") { "${it.key}=${it.value}" }
            android.util.Log.i(TAG, "  truth=$label (n=${group.size}): $breakdown")
        }
    }

    /** Ground truth "silence" is correct only when the classifier reported nothing at all. */
    private fun matches(label: String, predicted: AudioEventType?): Boolean = when (label) {
        "silence" -> predicted == null
        "snore" -> predicted == AudioEventType.SNORE
        "cough" -> predicted == AudioEventType.COUGH
        "talk" -> predicted == AudioEventType.TALK
        else -> false
    }

    private fun Double.roundTo1(): Double = (this * 10).roundToIntSafe() / 10.0
    private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
}
