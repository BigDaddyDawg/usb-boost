package com.usbboost.app

import android.content.Context

object SetupHelper {
    fun buildSettingsForSetup(context: Context, current: BoostSettings): BoostSettings {
        val dumpGranted = OutputMonitor.hasDumpPermission(context)
        return current.copy(
            enabled = true,
            autoCarMode = true,
            startOnBoot = true,
            enhancedDetection = dumpGranted,
            // Legacy mode works without PC/ADB — covers Spotify, YouTube Music, etc.
            legacyMode = !dumpGranted || current.legacyMode
        )
    }
}
