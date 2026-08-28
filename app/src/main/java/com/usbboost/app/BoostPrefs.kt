package com.usbboost.app

import android.content.Context
import android.content.SharedPreferences

data class BoostSettings(
    val enabled: Boolean = true,
    val autoCarMode: Boolean = true,
    val boostPercent: Int = 65,
    val bassPercent: Int = 55,
    val legacyMode: Boolean = false,
    val enhancedDetection: Boolean = true,
    val startOnBoot: Boolean = true
)

class BoostPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BoostSettings = BoostSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        autoCarMode = prefs.getBoolean(KEY_AUTO_CAR, true),
        boostPercent = prefs.getInt(KEY_BOOST, 65).coerceIn(0, 100),
        bassPercent = prefs.getInt(KEY_BASS, 55).coerceIn(0, 100),
        legacyMode = prefs.getBoolean(KEY_LEGACY, false),
        enhancedDetection = prefs.getBoolean(KEY_ENHANCED, true),
        startOnBoot = prefs.getBoolean(KEY_BOOT, true)
    )

    fun save(settings: BoostSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_AUTO_CAR, settings.autoCarMode)
            .putInt(KEY_BOOST, settings.boostPercent)
            .putInt(KEY_BASS, settings.bassPercent)
            .putBoolean(KEY_LEGACY, settings.legacyMode)
            .putBoolean(KEY_ENHANCED, settings.enhancedDetection)
            .putBoolean(KEY_BOOT, settings.startOnBoot)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "usb_boost_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_CAR = "auto_car"
        private const val KEY_BOOST = "boost"
        private const val KEY_BASS = "bass"
        private const val KEY_LEGACY = "legacy"
        private const val KEY_ENHANCED = "enhanced"
        private const val KEY_BOOT = "boot"
    }
}
