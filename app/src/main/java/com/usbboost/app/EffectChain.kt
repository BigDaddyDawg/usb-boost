package com.usbboost.app

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

class EffectChain(private val sessionId: Int) {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var dynamics: Any? = null

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
        val params = BoostLogic.maximizerParams(settings.boostPercent, carActive)
        val maximizerOk = params.useDynamics && DynamicsEffects.apply(dynamics, params)
        val leMb = if (maximizerOk) 0 else BoostLogic.loudnessFallbackMb(settings.boostPercent, carActive)
        val loudnessOk = applyLoudness(leMb)
        applyEq(settings, includePreamp = !maximizerOk && !loudnessOk)
        return isAttached()
    }

    fun isAttached(): Boolean {
        if (sessionId <= 0) return false
        return loudness?.enabled == true ||
            equalizer?.enabled == true ||
            DynamicsEffects.isEnabled(dynamics)
    }

    private fun applyLoudness(gainMb: Int): Boolean {
        val le = loudness ?: return false
        return runCatching {
            le.setTargetGain(gainMb)
            le.enabled = gainMb > 0
            true
        }.onFailure {
            Log.w(TAG, "LoudnessEnhancer update failed for session $sessionId", it)
        }.getOrDefault(false)
    }

    private fun applyEq(settings: BoostSettings, includePreamp: Boolean) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        if (bands <= 0) return

        val range = eq.bandLevelRange
        val min = range[0]
        val max = range[1]
        val shape = settings.resolvedEq()
        val wantOn = if (includePreamp) {
            BoostLogic.eqWantsOn(settings.boostPercent, shape)
        } else {
            BoostLogic.eqToneWantsOn(shape)
        }

        for (band in 0 until bands) {
            runCatching {
                val center = eq.getCenterFreq(band.toShort()) / 1000f
                val level = if (includePreamp) {
                    BoostLogic.eqBandMillibelsWithPreamp(center, settings.boostPercent, shape, min, max)
                } else {
                    BoostLogic.eqBandMillibels(center, shape, min, max)
                }
                eq.setBandLevel(band.toShort(), level)
            }.onFailure { Log.w(TAG, "EQ band $band failed for session $sessionId", it) }
        }
        runCatching { eq.enabled = wantOn }
            .onFailure { Log.w(TAG, "EQ enable failed for session $sessionId", it) }
    }

    private fun ensureEffects() {
        if (loudness == null) {
            loudness = runCatching { LoudnessEnhancer(sessionId) }
                .onFailure { Log.w(TAG, "LoudnessEnhancer failed for session $sessionId", it) }
                .getOrNull()
        }
        if (equalizer == null) {
            equalizer = runCatching { Equalizer(PRIORITY, sessionId) }
                .onFailure { Log.w(TAG, "Equalizer failed for session $sessionId", it) }
                .getOrNull()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dynamics == null) {
            dynamics = DynamicsEffects.create(sessionId, PRIORITY)
        }
    }

    fun release() {
        runCatching {
            loudness?.enabled = false
            loudness?.release()
        }
        loudness = null
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        equalizer = null
        DynamicsEffects.release(dynamics)
        dynamics = null
    }

    companion object {
        private const val TAG = "EffectChain"
        private const val PRIORITY = 100
    }
}
