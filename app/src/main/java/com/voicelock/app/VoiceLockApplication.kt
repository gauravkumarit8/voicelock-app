package com.voicelock.app

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import com.voicelock.app.receivers.ScreenStateReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceLockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // SCREEN_ON / SCREEN_OFF are NOT delivered to a manifest-declared
        // <receiver> on ANY Android version — this predates the API 26
        // implicit-broadcast restrictions and applies to these two actions
        // specifically, regardless of minSdk. They only reach a receiver
        // registered dynamically here, for the lifetime of the process.
        // (Previously this receiver was ALSO declared in the manifest with
        // an intent-filter for these actions — that declaration was dead
        // code and has been removed from AndroidManifest.xml.)
        registerReceiver(
            ScreenStateReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
    }
}
