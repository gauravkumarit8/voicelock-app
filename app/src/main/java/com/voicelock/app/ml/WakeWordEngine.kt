package com.voicelock.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
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
 * CAVEAT: the exact sample-count-per-window arithmetic below (~12400 raw
 * samples -> 76 melspec frames) is reconstructed from openWakeWord's public
 * documentation and community implementations, not verified against a live
 * run of their reference Python pipeline. Before relying on detection
 * accuracy, sanity-check this against openwakeword's own model.py / utils.py
 * (or just run their Python reference on the same audio and compare scores)
 * — a one-frame-off windowing bug wouldn't crash anything, it would just
 * silently produce worse detection than the model is actually capable of.
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

    /** Rolling raw-audio window — needs enough samples to produce a 76-frame melspectrogram. */
    private val rawAudioBuffer = ArrayDeque<Float>()

    /** Rolling buffer of embedding frames — classifier needs EMBEDDING_WINDOW consecutive frames. */
    private val embeddingHistory = ArrayDeque<FloatArray>()

    fun loadModels(
        melspecAsset: String = "models/melspectrogram.onnx",
        embeddingAsset: String = "models/embedding_model.onnx",
        classifierAsset: String = "models/hey_jarvis_v0.1.onnx" // swap for your trained phrase
    ) {
        melspecSession = env.createSession(context.assets.open(melspecAsset).use { it.readBytes() })
        embeddingSession = env.createSession(context.assets.open(embeddingAsset).use { it.readBytes() })
        classifierSession = env.createSession(context.assets.open(classifierAsset).use { it.readBytes() })
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
        while (rawAudioBuffer.size > MELSPEC_WINDOW_SAMPLES) rawAudioBuffer.removeFirst()
        if (rawAudioBuffer.size < MELSPEC_WINDOW_SAMPLES) return null // still filling the initial window

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
        const val CHUNK_SIZE_SAMPLES = 1280        // 80ms @ 16kHz, openWakeWord's native chunk size
        const val SAMPLE_RATE_HZ = 16000
        private const val MELSPEC_WINDOW_SAMPLES = 12400 // raw samples needed to produce 76 melspec frames
        private const val MELSPEC_FRAMES = 76
        private const val MEL_BINS = 32
        private const val EMBEDDING_WINDOW = 16    // classifier looks at 16 consecutive embedding frames
        private const val EMBEDDING_DIM = 96
    }
}
