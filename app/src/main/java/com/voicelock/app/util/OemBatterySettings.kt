package com.voicelock.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Deep-links into manufacturer-specific "autostart" / "protected apps" /
 * "no restrictions" settings screens. See PRD §15.1 and §17 Screen 4 —
 * these paths change across firmware versions and must be maintained
 * over the app's lifetime, not treated as a one-time build.
 *
 * Component names below are the commonly-documented ones as of 2026;
 * always wrap the launch in try/catch and fall back to the generic
 * app-details settings screen if the specific activity isn't found.
 */
object OemBatterySettings {

    private data class OemIntent(val pkg: String, val cls: String)

    private val knownIntents: Map<String, List<OemIntent>> = mapOf(
        "xiaomi" to listOf(
            OemIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        ),
        "huawei" to listOf(
            OemIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "honor" to listOf(
            OemIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "oppo" to listOf(
            OemIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            OemIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        ),
        "vivo" to listOf(
            OemIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        ),
        "oneplus" to listOf(
            OemIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        ),
        "samsung" to listOf(
            OemIntent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        )
    )

    /** Only show Onboarding Screen 4 for manufacturers we actually have a deep link for. */
    fun isKnownRestrictiveOem(): Boolean =
        knownIntents.containsKey(Build.MANUFACTURER.lowercase())

    fun manufacturerDisplayName(): String =
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** Attempts each known intent for this OEM; returns true if one launched. */
    fun openOemSettings(context: Context): Boolean {
        val candidates = knownIntents[Build.MANUFACTURER.lowercase()] ?: return false
        for (candidate in candidates) {
            try {
                val intent = Intent().apply {
                    component = android.content.ComponentName(candidate.pkg, candidate.cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    /** Fallback: generic per-app battery settings screen, always available on stock Android. */
    fun openGenericBatterySettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
