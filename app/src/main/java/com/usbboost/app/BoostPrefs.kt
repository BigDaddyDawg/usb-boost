package com.usbboost.app

import android.content.Context
import android.content.SharedPreferences

data class BoostSettings(
    val enabled: Boolean = false,
    val autoCarMode: Boolean = false,
    val boostPercent: Int = 65,
    val bassPercent: Int = 0,
    val legacyMode: Boolean = true,
    val enhancedDetection: Boolean = true,
    val startOnBoot: Boolean = true
) {
    fun boostDecibels(): Float = (boostPercent / 100f) * MAX_BOOST_DB

    companion object {
        const val MAX_BOOST_DB = 12f
    }
}

class BoostPrefs(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BoostSettings = BoostSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        autoCarMode = prefs.getBoolean(KEY_AUTO_CAR, false),
        boostPercent = prefs.getInt(KEY_BOOST, 65).coerceIn(0, 100),
        bassPercent = prefs.getInt(KEY_BASS, 0).coerceIn(0, 100),
        legacyMode = prefs.getBoolean(KEY_LEGACY, true),
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

    fun applyOutOfBoxDefaults() {
        if (!prefs.getBoolean(KEY_V12_SAFE, false)) {
            save(
                load().copy(
                    enabled = false,
                    autoCarMode = false,
                    legacyMode = true,
                    startOnBoot = true,
                    enhancedDetection = true
                )
            )
            prefs.edit()
                .putBoolean(KEY_V12_SAFE, true)
                .putBoolean(KEY_V13_SPEAKER, true)
                .putBoolean(KEY_V14_CLEAN_GAIN, true)
                .apply()
            return
        }
        if (!prefs.getBoolean(KEY_V13_SPEAKER, false)) {
            save(load().copy(autoCarMode = false))
            prefs.edit().putBoolean(KEY_V13_SPEAKER, true).apply()
        }
        if (!prefs.getBoolean(KEY_V14_CLEAN_GAIN, false)) {
            save(load().copy(bassPercent = 0))
            prefs.edit().putBoolean(KEY_V14_CLEAN_GAIN, true).apply()
        }
        save(
            load().copy(
                legacyMode = true,
                startOnBoot = true,
                enhancedDetection = true
            )
        )
    }

    fun batteryPromptShown(): Boolean = prefs.getBoolean(KEY_BATTERY_PROMPT, false)

    fun markBatteryPromptShown() {
        prefs.edit().putBoolean(KEY_BATTERY_PROMPT, true).apply()
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
        private const val KEY_BATTERY_PROMPT = "battery_prompt"
        private const val KEY_V12_SAFE = "v12_safe_launch"
        private const val KEY_V13_SPEAKER = "v13_boost_on_speaker"
        private const val KEY_V14_CLEAN_GAIN = "v14_clean_eq_gain"
    }
}
