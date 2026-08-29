package com.usbboost.app

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log

class EffectChain(private val sessionId: Int) {
    private var dynamics: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
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
        val boostDb = BoostLogic.boostDecibels(settings.boostPercent)
        val bassStrength = BoostLogic.bassStrength(settings.bassPercent)

        runCatching {
            val fx = dynamics ?: return@runCatching
            fx.setInputGainAllChannelsTo(boostDb)
            fx.enabled = boostDb > 0f
        }.onFailure { Log.w(TAG, "Gain update failed for session $sessionId", it) }

        runCatching {
            val fx = bassBoost ?: return@runCatching
            fx.setStrength(bassStrength.toShort())
            fx.enabled = bassStrength > 0
        }.onFailure { Log.w(TAG, "Bass update failed for session $sessionId", it) }

        applyEq(settings, fallbackPreamp = dynamics == null)
        return isAttached()
    }

    fun isAttached(): Boolean {
        if (sessionId <= 0) return false
        return dynamics?.enabled == true || equalizer?.enabled == true || bassBoost?.enabled == true
    }

    private fun applyEq(settings: BoostSettings, fallbackPreamp: Boolean) {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        if (bands <= 0) return

        val range = eq.bandLevelRange
        val min = range[0]
        val max = range[1]

        for (band in 0 until bands) {
            runCatching {
                val center = eq.getCenterFreq(band.toShort()) / 1000f
                val gain = if (fallbackPreamp) {
                    BoostLogic.eqFallbackGainFraction(center, settings.boostPercent, settings.bassPercent)
                } else {
                    BoostLogic.eqBassGainFraction(center, settings.bassPercent)
                }
                eq.setBandLevel(band.toShort(), BoostLogic.bandLevelMillibels(gain, min, max))
            }
        }
        val wantEq = settings.bassPercent > 0 || (fallbackPreamp && settings.boostPercent > 0)
        eq.enabled = wantEq
    }

    private fun ensureEffects() {
        if (dynamics == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dynamics = createDynamics()
        }
        if (equalizer == null) {
            equalizer = createEffect("Equalizer") {
                Equalizer(PRIORITY, sessionId).also { it.enabled = true }
            }
        }
        if (bassBoost == null) {
            bassBoost = createEffect("BassBoost") {
                BassBoost(PRIORITY, sessionId).also { it.enabled = true }
            }
        }
    }

    private fun createDynamics(): DynamicsProcessing? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        for (channels in intArrayOf(2, 1)) {
            val created = runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channels,
                    false, 0,
                    false, 0,
                    false, 0,
                    false
                ).build()
                DynamicsProcessing(PRIORITY, sessionId, config).also { it.enabled = true }
            }.onFailure {
                Log.w(TAG, "DynamicsProcessing($channels ch) failed for session $sessionId", it)
            }.getOrNull()
            if (created != null) {
                Log.i(TAG, "Using input gain (no compressor) on session $sessionId, $channels ch")
                return created
            }
        }
        return null
    }

    private fun <T> createEffect(name: String, factory: () -> T): T? {
        return runCatching { factory() }
            .onFailure { Log.w(TAG, "$name failed for session $sessionId", it) }
            .getOrNull()
    }

    fun release() {
        listOf(dynamics, equalizer, bassBoost).forEach { effect ->
            runCatching {
                effect?.enabled = false
                effect?.release()
            }
        }
        dynamics = null
        equalizer = null
        bassBoost = null
    }

    companion object {
        private const val TAG = "EffectChain"
        private const val PRIORITY = 100
    }
}
