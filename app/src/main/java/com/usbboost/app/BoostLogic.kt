package com.usbboost.app

import kotlin.math.pow

data class MaximizerParams(
    val useDynamics: Boolean,
    val inputGainDb: Float,
    val presenceDb: Float,
    val bassPostGainDb: Float,
    val midPostGainDb: Float,
    val highPostGainDb: Float,
    val mbcRatio: Float,
    val mbcThresholdDb: Float,
    val limiterThresholdDb: Float,
    val limiterPostGainDb: Float,
    val loudnessEnhancerMb: Int
) {
    /** Headline gain: 6 dB ≈ 2× level, 9.5 dB ≈ 3×. */
    val displayedDb: Float
        get() = if (useDynamics) midPostGainDb else loudnessEnhancerMb / 100f

    val enabled: Boolean get() = displayedDb > 0.05f
}

object BoostLogic {
    /** 6 dB = 2× amplitude, 9.5 dB ≈ 3×. Car maximizer hits this on the vocal/music band. */
    const val TWO_X_DB = 6f
    const val THREE_X_DB = 9.5f

    fun millibels(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * BoostSettings.MAX_BOOST_DB * 100f).toInt()
    }

    fun boostDecibels(percent: Int): Float {
        return (percent.coerceIn(0, 100) / 100f) * BoostSettings.MAX_BOOST_DB
    }

    fun appliedDecibels(percent: Int, carActive: Boolean): Float =
        maximizerParams(percent, carActive).displayedDb

    fun amplitudeMultiplier(db: Float): Float = 10f.pow(db / 20f)

    /**
     * LoudnessEnhancer target when DynamicsProcessing is missing.
     * Matches the 2–3× car target so fallback is still a real jump.
     */
    fun loudnessFallbackMb(percent: Int, carActive: Boolean): Int {
        val t = percent.coerceIn(0, 100) / 100f
        if (t <= 0f) return 0
        val db = if (carActive) THREE_X_DB * t else 8f * t
        return (db * 100f).toInt()
    }

    /**
     * USB/car: upward-loudness maximizer (MBC makeup on mids + limiter).
     * Peaks stay near 0 dBFS; RMS/vocals come up ~2–3× without scooping mids.
     * Phone speaker: LoudnessEnhancer only, so pockets do not get the car treatment.
     */
    fun maximizerParams(percent: Int, carActive: Boolean): MaximizerParams {
        val t = percent.coerceIn(0, 100) / 100f
        if (t <= 0f) {
            return MaximizerParams(
                useDynamics = false,
                inputGainDb = 0f,
                presenceDb = 0f,
                bassPostGainDb = 0f,
                midPostGainDb = 0f,
                highPostGainDb = 0f,
                mbcRatio = 1f,
                mbcThresholdDb = 0f,
                limiterThresholdDb = -0.3f,
                limiterPostGainDb = 0f,
                loudnessEnhancerMb = 0
            )
        }
        if (!carActive) {
            return MaximizerParams(
                useDynamics = false,
                inputGainDb = 0f,
                presenceDb = 0f,
                bassPostGainDb = 0f,
                midPostGainDb = 0f,
                highPostGainDb = 0f,
                mbcRatio = 1f,
                mbcThresholdDb = 0f,
                limiterThresholdDb = -0.3f,
                limiterPostGainDb = 0f,
                loudnessEnhancerMb = (8f * t * 100f).toInt()
            )
        }
        return MaximizerParams(
            useDynamics = true,
            inputGainDb = 2.5f * t,
            presenceDb = 1.5f * t,
            bassPostGainDb = TWO_X_DB * t,
            midPostGainDb = THREE_X_DB * t,
            highPostGainDb = 6.5f * t,
            mbcRatio = 1.6f + 0.8f * t,
            mbcThresholdDb = -14f - 6f * t,
            limiterThresholdDb = -0.3f,
            limiterPostGainDb = 1f * t,
            loudnessEnhancerMb = 0
        )
    }

    fun sessionsToProcess(discovered: Set<Int>, legacyMode: Boolean): Set<Int> {
        val real = discovered.filter { it > 0 }.toSet()
        if (real.isNotEmpty()) return real
        if (legacyMode) return setOf(0)
        return emptySet()
    }

    fun shouldApplyEffects(enabled: Boolean, autoCarMode: Boolean, carActive: Boolean): Boolean {
        if (!enabled) return false
        return !autoCarMode || carActive
    }

    fun isRealSession(id: Int): Boolean = id > 0

    fun eqWantsOn(boostPercent: Int, eq: EqBands): Boolean =
        boostPercent > 0 || !eq.isFlat()

    fun eqToneWantsOn(eq: EqBands): Boolean = !eq.isFlat()

    /**
     * Tone shape only. Boost/volume lives on the maximizer — stuffing the same
     * preamp into every EQ band clips on USB DACs and a limiter then ducks the mix.
     */
    fun eqBandMillibels(
        centerHz: Float,
        eq: EqBands,
        min: Short,
        max: Short
    ): Short {
        val mb = EqShapes.dbFor(centerHz, eq) * 100
        return mb.coerceIn(min.toInt(), max.toInt()).toShort()
    }

    /** Last-resort when neither maximizer nor LoudnessEnhancer can attach. */
    fun eqBandMillibelsWithPreamp(
        centerHz: Float,
        boostPercent: Int,
        eq: EqBands,
        min: Short,
        max: Short
    ): Short {
        val mb = millibels(boostPercent) + EqShapes.dbFor(centerHz, eq) * 100
        return mb.coerceIn(min.toInt(), max.toInt()).toShort()
    }

    fun bumpBoost(percent: Int, delta: Int): Int = (percent + delta).coerceIn(0, 100)
}
