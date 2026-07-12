package com.voicelock.app.ml

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Thin wrapper around AudioRecord, 16kHz mono float PCM (the format both
 * WakeWordEngine and SpeakerVerificationEngine expect). Used by:
 *  - WakeWordService: continuous CHUNK_SIZE_SAMPLES (1280) chunks while screen is on
 *  - EnrollmentScreen: one-shot capture-until-stopped for each enrollment take
 */
object AudioCapture {

    const val SAMPLE_RATE_HZ = WakeWordEngine.SAMPLE_RATE_HZ

    /**
     * Emits successive chunks of [chunkSizeSamples] raw float PCM samples,
     * normalized to [-1, 1], until the collecting coroutine is cancelled.
     * Caller must hold RECORD_AUDIO permission before calling this.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun chunkStream(chunkSizeSamples: Int = WakeWordEngine.CHUNK_SIZE_SAMPLES): Flow<FloatArray> = callbackFlow {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = maxOf(minBufferBytes, chunkSizeSamples * 2 * 4) // headroom, 2 bytes/sample

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        val shortBuffer = ShortArray(chunkSizeSamples)
        audioRecord.startRecording()

        // A dedicated thread rather than a coroutine loop with delay() —
        // AudioRecord.read() blocks until it has data, which is exactly the
        // pacing we want (no manual sleep/duty-cycle math needed here).
        val thread = Thread {
            try {
                while (!isClosedForSend) {
                    val read = audioRecord.read(shortBuffer, 0, chunkSizeSamples)
                    if (read == chunkSizeSamples) {
                        val floatChunk = FloatArray(chunkSizeSamples) { i ->
                            shortBuffer[i] / 32768f // normalize Int16 -> [-1, 1]
                        }
                        trySend(floatChunk)
                    }
                }
            } catch (_: Exception) {
                // Stream cancelled or AudioRecord torn down concurrently — expected on stop().
            }
        }
        thread.start()

        awaitClose {
            audioRecord.stop()
            audioRecord.release()
            thread.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * One-shot capture for enrollment: records while [shouldContinue] returns
     * true, then returns all captured samples concatenated as one FloatArray.
     * Caller flips a mutable flag to false (e.g. on a button tap) to stop —
     * see EnrollmentScreen for the calling pattern.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun recordUntilStopped(shouldContinue: () -> Boolean): FloatArray {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferBytes
        )
        val allSamples = mutableListOf<Float>()
        val chunk = ShortArray(1024)

        try {
            audioRecord.startRecording()
            while (shouldContinue()) {
                val read = audioRecord.read(chunk, 0, chunk.size)
                if (read > 0) {
                    for (i in 0 until read) allSamples.add(chunk[i] / 32768f)
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
        return allSamples.toFloatArray()
    }
}
