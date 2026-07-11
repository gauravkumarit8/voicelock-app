package com.voicelock.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Produces a 256-dim speaker embedding from ~1.5s of 16kHz mono audio and
 * compares it against the enrolled voiceprint via cosine similarity.
 *
 * MODEL FILE NOT INCLUDED: place a pretrained, permissively-licensed
 * (Apache/MIT) speaker-embedding model, converted to ONNX, at
 * app/src/main/assets/models/speaker_embedding.onnx.
 * See PRD §16 — reuse an existing pretrained model rather than training
 * your own from scratch for v1.
 *
 * Realistic accuracy expectations for short utterances are documented in
 * PRD §8 Risk 4 — this is a convenience lock, not a security-critical
 * biometric, and the sensitivity threshold should stay user-adjustable.
 */
@Singleton
class SpeakerVerificationEngine @Inject constructor(
    private val context: Context
) {
    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun loadModel(assetPath: String = "models/speaker_embedding.onnx") {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        session = env.createSession(bytes)
    }

    fun release() {
        session?.close()
        session = null
    }

    /** @param audioSamples raw 16kHz mono PCM float samples, ~1.5s of audio. */
    fun embed(audioSamples: FloatArray): FloatArray {
        val activeSession = session ?: error("SpeakerVerificationEngine.loadModel() must be called first")
        val inputName = activeSession.inputNames.iterator().next()
        val shape = longArrayOf(1, audioSamples.size.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(audioSamples), shape).use { tensor ->
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0].value as Array<FloatArray>
                return output[0]
            }
        }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-6f)
    }
}
