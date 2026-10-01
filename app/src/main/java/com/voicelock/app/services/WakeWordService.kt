package com.voicelock.app.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.voicelock.app.diagnostics.DiagnosticsLog
import com.voicelock.app.ml.AudioCapture
import com.voicelock.app.ml.WakeWordEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Persistent foreground service (type=microphone) — the process anchor for VoiceLock.
 *
 * Why persistent: SCREEN_ON/SCREEN_OFF can only be received by a dynamically
 * registered receiver, and that receiver dies with the process. Keeping this
 * foreground service alive keeps the process alive, so the receiver below
 * keeps working. The microphone itself is only OPEN while the screen is on:
 *   SCREEN_ON  -> start AudioRecord + wake-word pipeline
 *   SCREEN_OFF -> stop AudioRecord (no recording with the screen off)
 *
 * Started from the Home screen (app in foreground, so the API 34+ microphone
 * FGS start rules are satisfied). START_STICKY lets the system restart it
 * after a kill; that restart is allowed because the app holds the battery
 * optimization exemption. After a REBOOT it cannot restart itself
 * (BOOT_COMPLETED can't start microphone FGS) — BootCompletedReceiver posts
 * a "tap to resume" notification instead.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var wakeWordEngine: WakeWordEngine

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var listeningJob: Job? = null
    private var modelsLoaded = false
    private var lastTriggerMs = 0L
    private var chunkCounter = 0

    /** Rolling ~2s of raw audio, handed to VoiceAuthService when the wake word fires. */
    private val trailingAudio = ArrayDeque<Float>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startListening()
                Intent.ACTION_SCREEN_OFF -> stopListening()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.setServiceRunning(true)
        DiagnosticsLog.log(TAG, "Service created")
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Service may be (re)started while the screen is already on.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DiagnosticsLog.log(TAG, "onStartCommand (system may be restarting the service)")
        return START_STICKY
    }

    @Synchronized
    private fun startListening() {
        if (listeningJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO not granted, stopping service")
            DiagnosticsLog.log(TAG, "BLOCKED: RECORD_AUDIO permission not granted — stopping")
            stopSelf()
            return
        }
        listeningJob = serviceScope.launch {
            try {
                if (!modelsLoaded) {
                    wakeWordEngine.loadModels()
                    modelsLoaded = true
                }
                wakeWordEngine.resetHistory()
                trailingAudio.clear()
                chunkCounter = 0
                Log.i(TAG, "Screen on — listening")
                DiagnosticsLog.setMicOpen(true)
                DiagnosticsLog.log(TAG, "Screen ON — mic opened, listening for wake word")
                AudioCapture.chunkStream(WakeWordEngine.CHUNK_SIZE_SAMPLES)
                    .catch { e -> Log.e(TAG, "Audio capture stream failed", e) }
                    .collect { chunk ->
                        appendToTrailingBuffer(chunk)
                        val confidence = wakeWordEngine.processChunk(chunk) ?: return@collect
                        chunkCounter++
                        if (confidence >= DIAGNOSTIC_LOG_MIN_CONFIDENCE) {
                            DiagnosticsLog.log(TAG, "wake-word confidence=%.3f".format(confidence))
                        } else if (chunkCounter % PERIODIC_LOG_EVERY_N_CHUNKS == 0) {
                            // Chunks run every ~80ms, so every 12th is roughly once a second —
                            // proves the pipeline is alive and shows real numbers even when
                            // they're too low to clear the "interesting" threshold above.
                            DiagnosticsLog.log(TAG, "(quiet) wake-word confidence=%.4f".format(confidence))
                        }
                        if (confidence >= WAKE_WORD_THRESHOLD) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastTriggerMs < TRIGGER_COOLDOWN_MS) return@collect
                            lastTriggerMs = now
                            Log.i(TAG, "Wake word detected, confidence=$confidence")
                            DiagnosticsLog.log(TAG, "WAKE WORD DETECTED confidence=%.3f — checking speaker".format(confidence))
                            triggerVoiceAuth()
                            wakeWordEngine.resetHistory() // avoid re-triggering on the same utterance
                        }
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing RECORD_AUDIO permission, stopping", e)
                DiagnosticsLog.log(TAG, "ERROR: SecurityException — ${e.message}")
                stopSelf()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Listening failed", e)
                DiagnosticsLog.log(TAG, "ERROR: ${e.javaClass.simpleName} — ${e.message}")
            }
        }
    }

    @Synchronized
    private fun stopListening() {
        if (listeningJob?.isActive == true) {
            Log.i(TAG, "Screen off — mic closed")
            DiagnosticsLog.log(TAG, "Screen OFF — mic closed")
        }
        DiagnosticsLog.setMicOpen(false)
        listeningJob?.cancel()
        listeningJob = null
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
        DiagnosticsLog.setServiceRunning(false)
        DiagnosticsLog.setMicOpen(false)
        DiagnosticsLog.log(TAG, "Service destroyed")
        runCatching { unregisterReceiver(screenReceiver) }
        serviceScope.cancel()
        if (modelsLoaded) wakeWordEngine.release()
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
            .setContentTitle("VoiceLock is ready")
            .setContentText("Listens only while your screen is on")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIFICATION_ID = 1001
        private const val TRIGGER_COOLDOWN_MS = 3000L

        private const val WAKE_WORD_THRESHOLD = 0.5f
        private const val DIAGNOSTIC_LOG_MIN_CONFIDENCE = 0.15f
        private const val PERIODIC_LOG_EVERY_N_CHUNKS = 12 // ~once/second at 80ms chunks
        private const val TRAILING_BUFFER_SAMPLES = WakeWordEngine.SAMPLE_RATE_HZ * 2

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
