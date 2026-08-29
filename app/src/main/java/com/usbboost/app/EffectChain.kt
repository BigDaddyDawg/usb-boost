package com.usbboost.app

import android.media.audiofx.Equalizer
import android.util.Log

class EffectChain(private val sessionId: Int) {
    private var equalizer: Equalizer? = null

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
        applyEq(settings)
        return isAttached()
    }

    fun isAttached(): Boolean {
        if (sessionId <= 0) return false
        return equalizer?.enabled == true
    }

    private fun applyEq(settings: BoostSettings) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        if (bands <= 0) return

        val range = eq.bandLevelRange
        val min = range[0]
        val max = range[1]
        val wantOn = settings.boostPercent > 0 || settings.bassPercent > 0

        runCatching { eq.enabled = false }
        for (band in 0 until bands) {
            runCatching {
                val center = eq.getCenterFreq(band.toShort()) / 1000f
                eq.setBandLevel(
                    band.toShort(),
                    BoostLogic.eqBandMillibels(center, settings.boostPercent, settings.bassPercent, min, max)
                )
            }.onFailure { Log.w(TAG, "EQ band $band failed for session $sessionId", it) }
        }
        runCatching { eq.enabled = wantOn }
            .onFailure { Log.w(TAG, "EQ enable failed for session $sessionId", it) }
    }

    private fun ensureEffects() {
        if (equalizer != null) return
        equalizer = runCatching { Equalizer(PRIORITY, sessionId) }
            .onFailure { Log.w(TAG, "Equalizer failed for session $sessionId", it) }
            .getOrNull()
    }

    fun release() {
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        equalizer = null
    }

    companion object {
        private const val TAG = "EffectChain"
        private const val PRIORITY = 1
    }
}
