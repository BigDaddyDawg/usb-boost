package com.usbboost.app

import android.content.Context

data class SetupResult(
    val dumpGranted: Boolean,
    val usedLegacyFallback: Boolean,
    val usedShizuku: Boolean
)

object SetupHelper {
    fun applyPhoneFriendlyDefaults(context: Context): SetupResult {
        var dumpGranted = OutputMonitor.hasDumpPermission(context)
        var usedLegacy = false
        var usedShizuku = false

        if (!dumpGranted && EnhancedPermissionHelper.shizukuAvailable()) {
            usedShizuku = true
            if (EnhancedPermissionHelper.tryGrantDumpViaShizuku(context)) {
                dumpGranted = true
            }
        }

        if (!dumpGranted) {
            usedLegacy = true
        }

        return SetupResult(
            dumpGranted = dumpGranted,
            usedLegacyFallback = usedLegacy,
            usedShizuku = usedShizuku
        )
    }

    fun buildSettingsForSetup(context: Context, current: BoostSettings): BoostSettings {
        val result = applyPhoneFriendlyDefaults(context)
        return current.copy(
            enabled = true,
            autoCarMode = true,
            startOnBoot = true,
            enhancedDetection = result.dumpGranted,
            legacyMode = result.usedLegacyFallback || current.legacyMode
        )
    }
}
