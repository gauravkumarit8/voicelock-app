package com.voicelock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Deliberately does nothing beyond being present in the manifest.
 *
 * Android 15+ restricts BOOT_COMPLETED receivers from launching several
 * foreground service types directly (a ForegroundServiceStartNotAllowedException
 * is thrown for the restricted types). We rely instead on ScreenStateReceiver
 * reacting the next time the user turns the screen on post-reboot — no mic
 * service needs to survive a reboot on its own, since it's screen-gated by
 * design anyway (PRD core requirement: never listens with the screen off).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty. See class doc.
    }
}
