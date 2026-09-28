package com.voicelock.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Screen on/off handling lives inside WakeWordService (a dynamically registered
// receiver must be owned by something that keeps the process alive).
@HiltAndroidApp
class VoiceLockApplication : Application()
