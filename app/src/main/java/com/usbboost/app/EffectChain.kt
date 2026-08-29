package com.usbboost.app

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class EffectChain(private val sessionId: Int) {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null

    fun apply(settings: BoostSettings, carActive: Boolean): Boolean {
        if (!settings.enabled) {
            release()
            return false
        }

        val active = BoostLogic.shouldApplyEffects(settings.enabled, settings.autoCarMode, carActive)
        if (!active) {
            release()
            return false
        }

        ensureEffects()
        val boostMb = BoostLogic.millibels(settings.boostPercent)
        val bassStrength = BoostLogic.bassStrength(settings.bassPercent)

        runCatching {
            val fx = loudness ?: return@runCatching
            fx.setTargetGain(boostMb)
            fx.enabled = boostMb > 0
        }.onFailure { Log.w(TAG, "Loudness update failed for session $sessionId", it) }

        runCatching {
            val fx = bassBoost ?: return@runCatching
            fx.setStrength(bassStrength.toShort())
            fx.enabled = bassStrength > 0
        }.onFailure { Log.w(TAG, "Bass update failed for session $sessionId", it) }

        applyEq(settings)
        return isAttached()
    }

    fun isAttached(): Boolean {
        if (sessionId <= 0) return false
        return loudness?.enabled == true || equalizer?.enabled == true || bassBoost?.enabled == true
    }

    private fun applyEq(settings: BoostSettings) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        if (bands <= 0) return

        val range = eq.bandLevelRange
        val min = range[0]
        val max = range[1]

        for (band in 0 until bands) {
            runCatching {
                val center = eq.getCenterFreq(band.toShort()) / 1000f
                val gain = BoostLogic.eqGainFraction(center, settings.boostPercent, settings.bassPercent)
                eq.setBandLevel(band.toShort(), BoostLogic.bandLevelMillibels(gain, min, max))
            }
        }
        eq.enabled = settings.boostPercent > 0 || settings.bassPercent > 0
    }

    private fun ensureEffects() {
        if (equalizer == null) {
            equalizer = createEffect("Equalizer") {
                Equalizer(PRIORITY, sessionId).also { it.enabled = true }
            }
        }
        if (loudness == null) {
            loudness = createEffect("LoudnessEnhancer") {
                LoudnessEnhancer(sessionId).also { it.enabled = true }
            }
        }
        if (bassBoost == null) {
            bassBoost = createEffect("BassBoost") {
                BassBoost(PRIORITY, sessionId).also { it.enabled = true }
            }
        }
    }

    private fun <T> createEffect(name: String, factory: () -> T): T? {
        return runCatching { factory() }
            .onFailure { Log.w(TAG, "$name failed for session $sessionId", it) }
            .getOrNull()
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

    companion object {
        private const val TAG = "EffectChain"
        private const val PRIORITY = 100
    }
}
