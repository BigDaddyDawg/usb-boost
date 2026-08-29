package com.usbboost.app

import android.content.Context
import android.content.SharedPreferences

data class BoostSettings(
    val enabled: Boolean = false,
    val autoCarMode: Boolean = false,
    val autoOnUsb: Boolean = false,
    val boostPercent: Int = 65,
    val preset: SoundPreset = SoundPreset.FLAT,
    val eq: EqBands = EqBands(),
    val homeBoost: Int = 65,
    val homePreset: SoundPreset = SoundPreset.FLAT,
    val homeEq: EqBands = EqBands(),
    val carBoost: Int = 100,
    val carPreset: SoundPreset = SoundPreset.FLAT,
    val carEq: EqBands = EqBands(),
    val legacyMode: Boolean = true,
    val enhancedDetection: Boolean = true,
    val startOnBoot: Boolean = true
) {
    fun boostDecibels(): Float = BoostLogic.boostDecibels(boostPercent)

    fun appliedDecibels(car: Boolean): Float = BoostLogic.appliedDecibels(boostPercent, car)

    fun resolvedEq(): EqBands =
        if (preset == SoundPreset.CUSTOM) eq.coerced() else EqShapes.forPreset(preset)

    fun writeBack(car: Boolean): BoostSettings = if (car) {
        copy(carBoost = boostPercent, carPreset = preset, carEq = eq)
    } else {
        copy(homeBoost = boostPercent, homePreset = preset, homeEq = eq)
    }

    fun applyProfile(car: Boolean): BoostSettings = if (car) {
        copy(boostPercent = carBoost, preset = carPreset, eq = carEq)
    } else {
        copy(boostPercent = homeBoost, preset = homePreset, eq = homeEq)
    }

    companion object {
        const val MAX_BOOST_DB = 19f
    }
}

class BoostPrefs(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BoostSettings {
        val preset = SoundPreset.fromKey(prefs.getString(KEY_PRESET, SoundPreset.FLAT.name) ?: SoundPreset.FLAT.name)
        val eq = loadEq("")
        return BoostSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            autoCarMode = prefs.getBoolean(KEY_AUTO_CAR, false),
            autoOnUsb = prefs.getBoolean(KEY_AUTO_USB, false),
            boostPercent = prefs.getInt(KEY_BOOST, 65).coerceIn(0, 100),
            preset = preset,
            eq = eq,
            homeBoost = prefs.getInt(KEY_HOME_BOOST, prefs.getInt(KEY_BOOST, 65)).coerceIn(0, 100),
            homePreset = SoundPreset.fromKey(prefs.getString(KEY_HOME_PRESET, preset.name) ?: preset.name),
            homeEq = loadEq("home_"),
            carBoost = prefs.getInt(KEY_CAR_BOOST, 100).coerceIn(0, 100),
            carPreset = SoundPreset.fromKey(prefs.getString(KEY_CAR_PRESET, SoundPreset.FLAT.name) ?: SoundPreset.FLAT.name),
            carEq = loadEq("car_"),
            legacyMode = prefs.getBoolean(KEY_LEGACY, true),
            enhancedDetection = prefs.getBoolean(KEY_ENHANCED, true),
            startOnBoot = prefs.getBoolean(KEY_BOOT, true)
        )
    }

    fun save(settings: BoostSettings) {
        val eq = settings.eq.coerced()
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putBoolean(KEY_AUTO_CAR, settings.autoCarMode)
            .putBoolean(KEY_AUTO_USB, settings.autoOnUsb)
            .putInt(KEY_BOOST, settings.boostPercent)
            .putString(KEY_PRESET, settings.preset.name)
            .putInt(KEY_HOME_BOOST, settings.homeBoost)
            .putString(KEY_HOME_PRESET, settings.homePreset.name)
            .putInt(KEY_CAR_BOOST, settings.carBoost)
            .putString(KEY_CAR_PRESET, settings.carPreset.name)
            .putBoolean(KEY_LEGACY, settings.legacyMode)
            .putBoolean(KEY_ENHANCED, settings.enhancedDetection)
            .putBoolean(KEY_BOOT, settings.startOnBoot)
            .apply()
        saveEq("", eq)
        saveEq("home_", settings.homeEq.coerced())
        saveEq("car_", settings.carEq.coerced())
    }

    private fun loadEq(prefix: String): EqBands = EqBands(
        bass = prefs.getInt(prefix + KEY_EQ_BASS, 0),
        lowMid = prefs.getInt(prefix + KEY_EQ_LOW_MID, 0),
        mid = prefs.getInt(prefix + KEY_EQ_MID, 0),
        presence = prefs.getInt(prefix + KEY_EQ_PRESENCE, 0),
        treble = prefs.getInt(prefix + KEY_EQ_TREBLE, 0)
    ).coerced()

    private fun saveEq(prefix: String, eq: EqBands) {
        prefs.edit()
            .putInt(prefix + KEY_EQ_BASS, eq.bass)
            .putInt(prefix + KEY_EQ_LOW_MID, eq.lowMid)
            .putInt(prefix + KEY_EQ_MID, eq.mid)
            .putInt(prefix + KEY_EQ_PRESENCE, eq.presence)
            .putInt(prefix + KEY_EQ_TREBLE, eq.treble)
            .apply()
    }

    fun applyOutOfBoxDefaults() {
        if (!prefs.getBoolean(KEY_V12_SAFE, false)) {
            save(
                load().copy(
                    enabled = false,
                    autoCarMode = false,
                    autoOnUsb = false,
                    legacyMode = true,
                    startOnBoot = true,
                    enhancedDetection = true
                )
            )
            prefs.edit()
                .putBoolean(KEY_V12_SAFE, true)
                .putBoolean(KEY_V13_SPEAKER, true)
                .putBoolean(KEY_V14_CLEAN_GAIN, true)
                .putBoolean(KEY_V15_EQ, true)
                .apply()
            return
        }
        if (!prefs.getBoolean(KEY_V13_SPEAKER, false)) {
            save(load().copy(autoCarMode = false))
            prefs.edit().putBoolean(KEY_V13_SPEAKER, true).apply()
        }
        if (!prefs.getBoolean(KEY_V14_CLEAN_GAIN, false)) {
            save(load().copy(eq = EqBands()))
            prefs.edit().putBoolean(KEY_V14_CLEAN_GAIN, true).apply()
        }
        if (!prefs.getBoolean(KEY_V15_EQ, false)) {
            val current = load()
            save(
                current.copy(
                    homeBoost = current.boostPercent,
                    homeEq = current.eq,
                    homePreset = current.preset,
                    carBoost = current.boostPercent.coerceAtLeast(70),
                    carEq = current.eq,
                    carPreset = current.preset
                )
            )
            prefs.edit().putBoolean(KEY_V15_EQ, true).apply()
        }
        if (!prefs.getBoolean(KEY_V16_LOUD, false)) {
            val current = load()
            val inCar = OutputWatcher.carActive(context)
            save(
                current.copy(
                    carBoost = 100,
                    boostPercent = if (inCar) 100 else current.boostPercent
                )
            )
            prefs.edit().putBoolean(KEY_V16_LOUD, true).apply()
        }
        save(
            load().copy(
                legacyMode = true,
                startOnBoot = true,
                enhancedDetection = true
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "usb_boost_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_CAR = "auto_car"
        private const val KEY_AUTO_USB = "auto_usb"
        private const val KEY_BOOST = "boost"
        private const val KEY_PRESET = "preset"
        private const val KEY_HOME_BOOST = "home_boost"
        private const val KEY_HOME_PRESET = "home_preset"
        private const val KEY_CAR_BOOST = "car_boost"
        private const val KEY_CAR_PRESET = "car_preset"
        private const val KEY_EQ_BASS = "eq_bass"
        private const val KEY_EQ_LOW_MID = "eq_low_mid"
        private const val KEY_EQ_MID = "eq_mid"
        private const val KEY_EQ_PRESENCE = "eq_presence"
        private const val KEY_EQ_TREBLE = "eq_treble"
        private const val KEY_LEGACY = "legacy"
        private const val KEY_ENHANCED = "enhanced"
        private const val KEY_BOOT = "boot"
        private const val KEY_V12_SAFE = "v12_safe_launch"
        private const val KEY_V13_SPEAKER = "v13_boost_on_speaker"
        private const val KEY_V14_CLEAN_GAIN = "v14_clean_eq_gain"
        private const val KEY_V15_EQ = "v15_eq_presets"
        private const val KEY_V16_LOUD = "v16_loudness_maximizer"
    }
}
