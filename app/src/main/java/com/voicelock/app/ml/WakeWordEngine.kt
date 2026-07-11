package com.voicelock.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps an openWakeWord-trained ONNX model (see PRD §16 — free, MIT-licensed,
 * trained via the official Piper-TTS synthetic pipeline). Expects the
 * standard openWakeWord feature pipeline: 16kHz mono audio -> melspectrogram
 * -> shared embedding backbone -> this classifier head.
 *
 * MODEL FILE NOT INCLUDED: place your trained model at
 * app/src/main/assets/models/wakeword_phrase.onnx before building.
 * See PRD §16 build order steps 2-3 for the free training pipeline.
 */
@Singleton
class WakeWordEngine @Inject constructor(
    private val context: Context
) {
    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun loadModel(assetPath: String = "models/wakeword_phrase.onnx") {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        session = env.createSession(bytes)
    }

    fun release() {
        session?.close()
        session = null
    }

    /**
     * @param melFeatures precomputed melspectrogram features for the current
     *   audio frame, shape matching the model's expected input.
     * @return detection confidence in [0, 1]; caller compares against the
     *   openWakeWord-recommended default threshold of 0.5 (tune per PRD §15.2).
     */
    fun detect(melFeatures: FloatArray): Float {
        val activeSession = session ?: error("WakeWordEngine.loadModel() must be called first")
        val inputName = activeSession.inputNames.iterator().next()
        val shape = longArrayOf(1, melFeatures.size.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(melFeatures), shape).use { tensor ->
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0].value as Array<FloatArray>
                return output[0][0]
            }
        }
    }
}
