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

    fun isRealSession(id: Int): Boolean = id > 0

    fun eqWantsOn(boostPercent: Int, eq: EqBands): Boolean =
        boostPercent > 0 || !eq.isFlat()

    /**
     * Preamp from the boost slider, plus the tone shape (preset or custom bands).
     * Millibels: 100 mB = 1 dB.
     */
    fun eqBandMillibels(
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
