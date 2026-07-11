package com.voicelock.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicelock.app.ml.WakeWordEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service, type=microphone (mandatory manifest declaration on
 * API 34+, see PRD §8 Risk 5). Only ever started while the screen is on and
 * the battery-optimization exemption is granted — see ScreenStateReceiver.
 *
 * NOTE: actual audio capture / duty-cycling implementation (AudioRecord loop,
 * melspectrogram feature extraction, 16ms-frame processing) is intentionally
 * left as a TODO here — it's mechanical but lengthy, and belongs in its own
 * reviewed PR rather than scaffolded blind. WakeWordEngine.detect() is ready
 * to be wired up to a real AudioRecord pipeline.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var wakeWordEngine: WakeWordEngine

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeWordEngine.loadModel()
        serviceScope.launch {
            // TODO: AudioRecord capture loop -> melspectrogram features ->
            // wakeWordEngine.detect(features) -> on threshold hit, start
            // VoiceAuthService with the trailing ~1.5s audio buffer.
        }
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
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
