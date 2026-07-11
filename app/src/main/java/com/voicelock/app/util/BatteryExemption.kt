package com.voicelock.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * See PRD §8 Risk 1: this exemption is what allows WakeWordService to start
 * from the SCREEN_ON background broadcast receiver at all on API 34+.
 * Without it, the OS treats the app as "in the background" with no
 * while-in-use RECORD_AUDIO grant and throws SecurityException immediately.
 */
object BatteryExemption {

    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun openBatterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
