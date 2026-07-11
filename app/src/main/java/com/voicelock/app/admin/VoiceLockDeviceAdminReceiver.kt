package com.voicelock.app.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Minimal Device Admin receiver. We only ever call lockNow() through this —
 * see PRD §8 Risk 3: this consumer lock/wipe use case is explicitly kept
 * alive by Google even though enterprise Device Admin policies (password
 * quality, camera disable, etc.) were deprecated in favor of Android
 * Enterprise / work profiles.
 */
class VoiceLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Device Admin was revoked (user did this manually in Settings).
        // The onboarding status store should reflect this so the home
        // screen re-surfaces the "incomplete setup" state (see PRD §17).
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, VoiceLockDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(componentName(context))
        }

        fun requestActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "VoiceLock can only lock your screen — never unlock it, read your data, or erase your phone."
                )
            }
        }
    }
}
