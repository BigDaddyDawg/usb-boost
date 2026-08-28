package com.usbboost.app

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class EffectChain(private val sessionId: Int) {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null

    fun apply(settings: BoostSettings, carActive: Boolean) {
        if (!settings.enabled) {
            release()
            return
        }

        val active = !settings.autoCarMode || carActive
        if (!active) {
            release()
            return
        }

        ensureEffects()
        val boostMb = percentToMillibels(settings.boostPercent)
        val bassStrength = ((settings.bassPercent / 100f) * 1000f).toInt().coerceIn(0, 1000)

        runCatching {
            loudness?.setTargetGain(boostMb)
            loudness?.enabled = boostMb > 0
        }.onFailure { Log.w(TAG, "Loudness update failed for session $sessionId", it) }

        runCatching {
            bassBoost?.setStrength(bassStrength.toShort())
            bassBoost?.enabled = bassStrength > 0
        }.onFailure { Log.w(TAG, "Bass update failed for session $sessionId", it) }

        applyCarEq(settings)
    }

    private fun applyCarEq(settings: BoostSettings) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        if (bands <= 0) return

        val range = eq.bandLevelRange
        val min = range[0]
        val max = range[1]
        val span = (max - min).toFloat()

        for (band in 0 until bands) {
            val center = eq.getCenterFreq(band.toShort()) / 1000f
            val gain = when {
                center < 120f -> settings.bassPercent / 100f * 0.85f
                center < 350f -> settings.bassPercent / 100f * 0.45f
                center in 800f..3500f -> settings.boostPercent / 100f * 0.35f
                center > 9000f -> settings.boostPercent / 100f * 0.15f
                else -> 0.12f
            }
            val level = (min + span * gain).toInt().toShort()
            runCatching { eq.setBandLevel(band.toShort(), level) }
        }
        eq.enabled = true
    }

    private fun ensureEffects() {
        if (equalizer == null) {
            equalizer = runCatching { Equalizer(0, sessionId) }
                .getOrNull()
                ?.also { it.enabled = true }
        }
        if (loudness == null) {
            loudness = runCatching { LoudnessEnhancer(sessionId) }
                .getOrNull()
                ?.also { it.enabled = true }
        }
        if (bassBoost == null) {
            bassBoost = runCatching { BassBoost(0, sessionId) }
                .getOrNull()
                ?.also { it.enabled = true }
        }
    }

    fun release() {
        listOf(equalizer, loudness, bassBoost).forEach { effect ->
            runCatching {
                effect?.enabled = false
                effect?.release()
            }
        }
        equalizer = null
        loudness = null
        bassBoost = null
    }

    private fun percentToMillibels(percent: Int): Int {
        // Up to 12 dB preamp at 100% slider.
        return ((percent / 100f) * BoostSettings.MAX_BOOST_DB * 100f).toInt()
    }

    companion object {
        private const val TAG = "EffectChain"
    }
}
