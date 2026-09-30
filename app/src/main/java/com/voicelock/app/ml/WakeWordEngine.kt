package com.voicelock.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real openWakeWord inference pipeline — THREE chained ONNX models, not one:
 *
 *   rolling raw 16kHz audio window -> melspectrogram.onnx -> 76 melspec frames
 *   76 melspec frames               -> embedding_model.onnx -> one 96-dim embedding
 *   16 consecutive embeddings       -> <phrase>.onnx classifier -> confidence score
 *
 * This matches openWakeWord's documented architecture (see
 * https://github.com/dscripka/openWakeWord). The three-model split, the
 * 76-frame melspectrogram window, and the 16-frame embedding window are
 * openWakeWord's own fixed design choices, not something we're free to
 * simplify — they come from how the pretrained embedding/classifier models
 * were trained.
 *
 * The raw-sample window size needed to get exactly 76 melspectrogram frames
 * is CALIBRATED at runtime (see calibrateMelspecWindow), not assumed: the
 * textbook (N - frame)/hop + 1 formula turned out to be one frame short
 * against the actual bundled model (2400 elements vs the required 2432), so
 * rather than hand-guess the model's real internal framing/padding, we
 * probe it directly on first load and search for the exact match.
 *
 * REMAINING CAVEAT: calibration only guarantees the *shape* is right (no more
 * OrtException crashes). It says nothing about whether the melspectrogram
 * *content* matches openWakeWord's Python reference bin-for-bin — that would
 * need comparing outputs against a live run of their pipeline on the same
 * audio, and is still worth doing before trusting detection accuracy.
 *
 * Bundled in app/src/main/assets/models/ for testing:
 *   - melspectrogram.onnx, embedding_model.onnx  (shared preprocessing — reuse for ANY phrase)
 *   - hey_jarvis_v0.1.onnx                        (stock pretrained phrase, for pipeline testing)
 * Swap only the classifier file for your own trained phrase later — the
 * melspectrogram/embedding models stay the same.
 */
@Singleton
class WakeWordEngine @Inject constructor(
    private val context: Context
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var melspecSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private var classifierSession: OrtSession? = null

    /** Rolling raw-audio window — needs enough samples to produce a MELSPEC_FRAMES-frame melspectrogram. */
    private val rawAudioBuffer = ArrayDeque<Float>()

    /** Rolling buffer of embedding frames — classifier needs EMBEDDING_WINDOW consecutive frames. */
    private val embeddingHistory = ArrayDeque<FloatArray>()

    /**
     * How many raw samples the bundled melspectrogram.onnx actually needs to emit exactly
     * MELSPEC_FRAMES frames. Determined empirically in loadModels() rather than assumed,
     * because the model's real internal framing (padding/centering) doesn't necessarily
     * match the textbook (N - frame)/hop + 1 formula — ours was off by exactly one frame
     * in testing (75 frames / 2400 elements instead of the required 76 / 2432).
     */
    private var melspecWindowSamples: Int = MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS

    fun loadModels(
        melspecAsset: String = "models/melspectrogram.onnx",
        embeddingAsset: String = "models/embedding_model.onnx",
        classifierAsset: String = "models/hey_jarvis_v0.1.onnx" // swap for your trained phrase
    ) {
        val melspec = env.createSession(context.assets.open(melspecAsset).use { it.readBytes() })
        melspecSession = melspec
        embeddingSession = env.createSession(context.assets.open(embeddingAsset).use { it.readBytes() })
        classifierSession = env.createSession(context.assets.open(classifierAsset).use { it.readBytes() })
        melspecWindowSamples = calibrateMelspecWindow(melspec)
    }

    /**
     * Find the raw-sample window length that makes melspectrogram.onnx emit exactly
     * MELSPEC_FRAMES * MEL_BINS elements, by actually running it and checking the
     * output size — starting from the textbook guess and searching outward.
     */
    private fun calibrateMelspecWindow(session: OrtSession): Int {
        val target = MELSPEC_FRAMES * MEL_BINS
        fun outputSizeFor(samples: Int): Int {
            val silence = FloatArray(samples)
            val inputName = session.inputNames.iterator().next()
            OnnxTensor.createTensor(env, FloatBuffer.wrap(silence), longArrayOf(1, samples.toLong())).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { result ->
                    return flattenToFloatArray(result[0].value).size
                }
            }
        }

        // The mismatch we saw (2400 vs 2432) is a small, fixed offset, not a scaling error,
        // so a narrow linear search around the guess is enough — try short increases first
        // (a model that pads/centers usually needs a *few* more samples, not fewer).
        for (delta in 0..CALIBRATION_SEARCH_RANGE) {
            val candidate = MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS + delta
            val size = outputSizeFor(candidate)
            if (size == target) {
                Log.i(TAG, "Calibrated melspectrogram window: $candidate samples (guess was ${MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS})")
                return candidate
            }
        }
        for (delta in 1..CALIBRATION_SEARCH_RANGE) {
            val candidate = MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS - delta
            if (candidate <= 0) break
            val size = outputSizeFor(candidate)
            if (size == target) {
                Log.i(TAG, "Calibrated melspectrogram window: $candidate samples (guess was ${MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS})")
                return candidate
            }
        }

        Log.e(TAG, "Could not calibrate melspectrogram window within ±$CALIBRATION_SEARCH_RANGE samples of the guess — wake-word detection will likely keep failing")
        return MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS
    }

    fun release() {
        melspecSession?.close(); melspecSession = null
        embeddingSession?.close(); embeddingSession = null
        classifierSession?.close(); classifierSession = null
        rawAudioBuffer.clear()
        embeddingHistory.clear()
    }

    /**
     * Feed one 80ms chunk (1280 samples at 16kHz) of raw mono audio at a time,
     * continuously, while the screen is on. Returns a detection confidence in
     * [0,1] once enough history has accumulated on both the raw-audio and
     * embedding windows, or null while still filling up (first ~1.5-2s after
     * WakeWordService starts).
     */
    fun processChunk(pcm1280: FloatArray): Float? {
        val melspec = melspecSession ?: error("call loadModels() first")
        val embedder = embeddingSession ?: error("call loadModels() first")
        val classifier = classifierSession ?: error("call loadModels() first")

        require(pcm1280.size == CHUNK_SIZE_SAMPLES) { "expected $CHUNK_SIZE_SAMPLES samples per chunk" }

        pcm1280.forEach { rawAudioBuffer.addLast(it) }
        while (rawAudioBuffer.size > melspecWindowSamples) rawAudioBuffer.removeFirst()
        if (rawAudioBuffer.size < melspecWindowSamples) return null // still filling the initial window

        // Stage 1: rolling raw-audio window -> melspectrogram frames
        val melFrames = runMelspectrogram(melspec, rawAudioBuffer.toFloatArray())

        // Stage 2: melspectrogram window -> one new 96-dim embedding
        val embedding = runEmbedding(embedder, melFrames)
        embeddingHistory.addLast(embedding)
        while (embeddingHistory.size > EMBEDDING_WINDOW) embeddingHistory.removeFirst()
        if (embeddingHistory.size < EMBEDDING_WINDOW) return null

        // Stage 3: sequence of embeddings -> classifier confidence
        return runClassifier(classifier, embeddingHistory.toList())
    }

    fun resetHistory() {
        rawAudioBuffer.clear()
        embeddingHistory.clear()
    }

    private fun runMelspectrogram(session: OrtSession, pcm: FloatArray): FloatArray {
        val inputName = session.inputNames.iterator().next()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(pcm), longArrayOf(1, pcm.size.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                return flattenToFloatArray(result[0].value)
            }
        }
    }

    private fun runEmbedding(session: OrtSession, melFrames: FloatArray): FloatArray {
        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1, MELSPEC_FRAMES.toLong(), MEL_BINS.toLong(), 1)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(melFrames), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                return flattenToFloatArray(result[0].value)
            }
        }
    }

    private fun runClassifier(session: OrtSession, embeddings: List<FloatArray>): Float {
        val inputName = session.inputNames.iterator().next()
        val flat = FloatArray(embeddings.size * EMBEDDING_DIM)
        embeddings.forEachIndexed { i, e -> e.copyInto(flat, i * EMBEDDING_DIM) }
        val shape = longArrayOf(1, embeddings.size.toLong(), EMBEDDING_DIM.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                return flattenToFloatArray(result[0].value)[0]
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenToFloatArray(value: Any): FloatArray {
        // ONNX Runtime returns nested Array<Array<...FloatArray>> depending on rank;
        // this walks arbitrary nesting depth down to a flat FloatArray.
        return when (value) {
            is FloatArray -> value
            is Array<*> -> value.flatMap { flattenToFloatArray(it!!).toList() }.toFloatArray()
            else -> error("Unexpected ONNX output type: ${value::class}")
        }
    }

    companion object {
        private const val TAG = "WakeWordEngine"
        const val CHUNK_SIZE_SAMPLES = 1280        // 80ms @ 16kHz, openWakeWord's native chunk size
        const val SAMPLE_RATE_HZ = 16000
        // Textbook (N - frame)/hop + 1 estimate for 76 frames; the real bundled model needed a
        // slightly different value (see calibrateMelspecWindow) — this is only the search's starting point.
        private const val MELSPEC_WINDOW_SAMPLES_INITIAL_GUESS = 12400
        private const val CALIBRATION_SEARCH_RANGE = 800
        private const val MELSPEC_FRAMES = 76
        private const val MEL_BINS = 32
        private const val EMBEDDING_WINDOW = 16    // classifier looks at 16 consecutive embedding frames
        private const val EMBEDDING_DIM = 96
    }
}
