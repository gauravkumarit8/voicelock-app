package com.voicelock.app.ml

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Produces a fixed-length voice "fingerprint" from ~1.5s of 16kHz mono audio
 * and compares two fingerprints via cosine similarity.
 *
 * IMPORTANT — this is a classical DSP feature extractor (log-mel-filterbank
 * mean/variance), not a trained neural speaker-embedding model. It has NO
 * external model file dependency, so the app is fully testable today without
 * needing to source/train/convert an ONNX model first.
 *
 * Trade-off, stated plainly: this will be meaningfully less accurate at
 * telling similar voices apart than a real trained embedding model (GE2E,
 * ECAPA-TDNN, etc. — see PRD §16). It's a legitimate baseline technique
 * (similar in spirit to classic i-vector-lite speaker features), good enough
 * to validate the whole pipeline and get real end-to-end testing today.
 * Swap in a trained ONNX model later (see WakeWordEngine for the pattern —
 * same loadModel()/embed() shape) once one is sourced, without needing to
 * change any caller (EnrollmentViewModel, VoiceAuthService).
 */
@Singleton
class SpeakerVerificationEngine @Inject constructor() {

    /** No-op — kept for interface parity with WakeWordEngine's loadModel()/release() lifecycle. */
    fun loadModel() { /* nothing to load — see class doc */ }
    fun release() { /* nothing to release */ }

    /**
     * @param audioSamples raw 16kHz mono PCM float samples, ~1.5s of audio.
     * @return a fixed-length (2 * MEL_BINS) embedding: per-mel-bin mean then
     *   per-mel-bin standard deviation of log-energy across frames.
     */
    fun embed(audioSamples: FloatArray): FloatArray {
        val frames = frameAudio(audioSamples)
        if (frames.isEmpty()) return FloatArray(MEL_BINS * 2)

        val melFilterbank = buildMelFilterbank()
        val logMelPerFrame = frames.map { frame ->
            val windowed = applyHammingWindow(frame)
            val spectrum = magnitudeSpectrum(windowed)
            val melEnergies = applyFilterbank(spectrum, melFilterbank)
            FloatArray(MEL_BINS) { i -> ln(max(melEnergies[i], 1e-6f)) }
        }

        val mean = FloatArray(MEL_BINS) { bin -> logMelPerFrame.map { it[bin] }.average().toFloat() }
        val stddev = FloatArray(MEL_BINS) { bin ->
            val variance = logMelPerFrame.map { (it[bin] - mean[bin]) * (it[bin] - mean[bin]) }.average()
            sqrt(variance).toFloat()
        }

        return l2Normalize(mean + stddev)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding size mismatch: ${a.size} vs ${b.size}" }
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // both already L2-normalized, so dot product == cosine similarity
    }

    // -------------------------------------------------------------------
    // DSP internals
    // -------------------------------------------------------------------

    private fun frameAudio(samples: FloatArray): List<FloatArray> {
        val frameSize = (FRAME_MS * SAMPLE_RATE_HZ / 1000)
        val hopSize = (HOP_MS * SAMPLE_RATE_HZ / 1000)
        if (samples.size < frameSize) return emptyList()
        val frames = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameSize <= samples.size) {
            frames.add(samples.copyOfRange(start, start + frameSize))
            start += hopSize
        }
        return frames
    }

    private fun applyHammingWindow(frame: FloatArray): FloatArray {
        val n = frame.size
        return FloatArray(n) { i ->
            val w = 0.54f - 0.46f * cos((2 * PI * i / (n - 1)).toFloat())
            frame[i] * w
        }
    }

    /** Zero-padded naive DFT magnitude spectrum — frame sizes here are small (~400 samples), fast enough. */
    private fun magnitudeSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val half = n / 2
        val magnitudes = FloatArray(half)
        for (k in 0 until half) {
            var re = 0.0
            var im = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                re += frame[t] * cos(angle)
                im += frame[t] * sin(angle)
            }
            magnitudes[k] = sqrt(re * re + im * im).toFloat()
        }
        return magnitudes
    }

    private fun buildMelFilterbank(): Array<FloatArray> {
        val fftBins = (FRAME_MS * SAMPLE_RATE_HZ / 1000) / 2
        val melMin = hzToMel(0f)
        val melMax = hzToMel(SAMPLE_RATE_HZ / 2f)
        val melPoints = FloatArray(MEL_BINS + 2) { i -> melMin + i * (melMax - melMin) / (MEL_BINS + 1) }
        val hzPoints = melPoints.map { melToHz(it) }
        val binPoints = hzPoints.map { (it * fftBins / (SAMPLE_RATE_HZ / 2f)).toInt().coerceIn(0, fftBins - 1) }

        return Array(MEL_BINS) { m ->
            val filter = FloatArray(fftBins)
            val left = binPoints[m]; val center = binPoints[m + 1]; val right = binPoints[m + 2]
            for (k in left until center) {
                if (center > left) filter[k] = (k - left).toFloat() / (center - left)
            }
            for (k in center until right) {
                if (right > center) filter[k] = (right - k).toFloat() / (right - center)
            }
            filter
        }
    }

    private fun applyFilterbank(spectrum: FloatArray, filterbank: Array<FloatArray>): FloatArray =
        FloatArray(filterbank.size) { m ->
            var sum = 0f
            for (k in spectrum.indices) sum += spectrum[k] * filterbank[m][k]
            sum
        }

    private fun hzToMel(hz: Float): Float = 2595f * kotlin.math.log10(1f + hz / 700f)
    private fun melToHz(mel: Float): Float = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var norm = 0f
        for (v in vec) norm += v * v
        norm = sqrt(norm).coerceAtLeast(1e-6f)
        return FloatArray(vec.size) { i -> vec[i] / norm }
    }

    private operator fun FloatArray.plus(other: FloatArray): FloatArray =
        FloatArray(this.size + other.size) { i -> if (i < this.size) this[i] else other[i - this.size] }

    companion object {
        private const val SAMPLE_RATE_HZ = 16000
        private const val FRAME_MS = 25
        private const val HOP_MS = 10
        private const val MEL_BINS = 26
    }
}
