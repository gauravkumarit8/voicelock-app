package com.voicelock.app.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockManager @Inject constructor(
    private val context: Context
) {
    /** Returns true if the lock actually fired. */
    fun lockNow(): Boolean {
        if (!VoiceLockDeviceAdminReceiver.isActive(context)) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.lockNow()
        return true
    }
}
