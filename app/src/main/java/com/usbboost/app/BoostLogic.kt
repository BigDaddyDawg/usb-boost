package com.usbboost.app

object BoostLogic {
    fun millibels(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * BoostSettings.MAX_BOOST_DB * 100f).toInt()
    }

    fun boostDecibels(percent: Int): Float {
        return (percent.coerceIn(0, 100) / 100f) * BoostSettings.MAX_BOOST_DB
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

    fun bassStrength(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * 1000f).toInt().coerceIn(0, 1000)
    }

    fun isRealSession(id: Int): Boolean = id > 0

    /**
     * One equalizer, one gain: every band gets the boost. Bass only adds on the low bands.
     * Values are millibels (100 mB = 1 dB), matching the slider — not a compressor.
     */
    fun eqBandMillibels(centerHz: Float, boostPercent: Int, bassPercent: Int, min: Short, max: Short): Short {
        var mb = millibels(boostPercent)
        when {
            centerHz < 120f -> mb += millibels(bassPercent)
            centerHz < 350f -> mb += (millibels(bassPercent) * 0.45f).toInt()
        }
        return mb.coerceIn(min.toInt(), max.toInt()).toShort()
    }
}
