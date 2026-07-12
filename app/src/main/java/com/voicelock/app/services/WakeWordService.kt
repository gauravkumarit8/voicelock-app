package com.voicelock.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voicelock.app.data.OnboardingStatusStore
import com.voicelock.app.ml.AudioCapture
import com.voicelock.app.ml.WakeWordEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service, type=microphone (mandatory manifest declaration on
 * API 34+, see PRD §8 Risk 5). Only ever started while the screen is on and
 * the battery-optimization exemption is granted — see ScreenStateReceiver.
 *
 * Runs the real 3-stage openWakeWord pipeline (see WakeWordEngine) over a
 * continuous AudioRecord stream. On detection above WAKE_WORD_THRESHOLD,
 * hands the trailing ~1.5s audio buffer to VoiceAuthService for speaker
 * verification and stops listening until that resolves.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var wakeWordEngine: WakeWordEngine
    @Inject lateinit var onboardingStatusStore: OnboardingStatusStore

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    /** Rolling ~1.5s of raw audio, handed to VoiceAuthService when the wake word fires. */
    private val trailingAudio = ArrayDeque<Float>()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeWordEngine.loadModels()
        startListening()
    }

    private fun startListening() {
        serviceScope.launch {
            try {
                AudioCapture.chunkStream(WakeWordEngine.CHUNK_SIZE_SAMPLES)
                    .catch { e -> Log.e(TAG, "Audio capture stream failed", e) }
                    .collect { chunk ->
                        appendToTrailingBuffer(chunk)

                        val confidence = wakeWordEngine.processChunk(chunk) ?: return@collect
                        if (confidence >= WAKE_WORD_THRESHOLD) {
                            Log.i(TAG, "Wake word detected, confidence=$confidence")
                            triggerVoiceAuth()
                            wakeWordEngine.resetHistory() // avoid re-triggering on the same utterance
                        }
                    }
            } catch (e: SecurityException) {
                // RECORD_AUDIO not actually granted despite our earlier checks
                // (e.g. revoked mid-session) — stop cleanly rather than crash-loop.
                Log.e(TAG, "Missing RECORD_AUDIO permission, stopping", e)
                stopSelf()
            }
        }
    }

    private fun appendToTrailingBuffer(chunk: FloatArray) {
        chunk.forEach { trailingAudio.addLast(it) }
        while (trailingAudio.size > TRAILING_BUFFER_SAMPLES) trailingAudio.removeFirst()
    }

    private fun triggerVoiceAuth() {
        val samples = trailingAudio.toFloatArray()
        val intent = Intent(this, VoiceAuthService::class.java).apply {
            putExtra(VoiceAuthService.EXTRA_AUDIO_SAMPLES, samples)
        }
        startService(intent)
    }

    override fun onDestroy() {
        wakeWordEngine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "voicelock_listening"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Voice Lock listening", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoiceLock is listening")
            .setContentText("Say your phrase to lock your phone")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIFICATION_ID = 1001

        // Default threshold; PRD §17 "Home Screen" eventually exposes this via
        // OnboardingStatusStore.sensitivity for the wake-word stage too (currently
        // that store's sensitivity value is used for speaker verification —
        // wake-word detection confidence and speaker-match confidence are
        // separate signals and may warrant separate tunable thresholds later).
        private const val WAKE_WORD_THRESHOLD = 0.5f
        private const val TRAILING_BUFFER_SAMPLES = WakeWordEngine.SAMPLE_RATE_HZ * 2 // ~2s, generous margin

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
