package com.voicelock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.voicelock.app.services.WakeWordService
import com.voicelock.app.util.BatteryExemption

/**
 * Core gatekeeper for PRD §8 Risk 1. A BroadcastReceiver has no visible UI,
 * so the app is considered "in the background" here. On API 34+, starting a
 * foregroundServiceType="microphone" service from the background throws
 * SecurityException UNLESS the app is exempt from battery optimizations.
 *
 * This receiver therefore checks the exemption BEFORE attempting to start
 * the service, rather than letting it crash and finding out from a crash
 * report. If the exemption is missing, it does not attempt to start the
 * service at all — the persistent home-screen warning banner (PRD §17)
 * is the correct place to prompt the user, not a crash loop here.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                if (!BatteryExemption.isExempt(context)) {
                    Log.w(TAG, "Battery exemption not granted — skipping WakeWordService start to avoid SecurityException")
                    return
                }
                WakeWordService.start(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                WakeWordService.stop(context)
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
}
