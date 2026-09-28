package com.voicelock.app.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.voicelock.app.data.OnboardingStatusStore
import com.voicelock.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BOOT_COMPLETED receivers cannot start a microphone foreground service on
 * modern Android, so after a reboot VoiceLock can't resume by itself.
 * Instead, if setup was completed, post a one-tap notification that opens
 * the app (whose Home screen starts WakeWordService).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                if (!OnboardingStatusStore(app).isFullySetUp.first()) return@launch
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel("voicelock_resume", "Resume VoiceLock", NotificationManager.IMPORTANCE_DEFAULT)
                )
                val open = PendingIntent.getActivity(
                    app, 0, Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                nm.notify(
                    1002,
                    NotificationCompat.Builder(app, "voicelock_resume")
                        .setContentTitle("Tap to turn VoiceLock back on")
                        .setContentText("Your phone restarted — open VoiceLock to resume voice lock")
                        .setSmallIcon(android.R.drawable.ic_lock_lock)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Exception) {
                // Notifications may be blocked (POST_NOTIFICATIONS) — nothing else to do.
            } finally {
                pending.finish()
            }
        }
    }
}
