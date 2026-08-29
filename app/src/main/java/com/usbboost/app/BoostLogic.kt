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
     * Map 0..1 onto the equalizer's millibel range, relative to unity (0).
     */
    fun bandLevelMillibels(gainFraction: Float, min: Short, max: Short): Short {
        val level = (gainFraction.coerceIn(0f, 1f) * max).toInt()
        return level.coerceIn(min.toInt(), max.toInt()).toShort()
    }

    /** Bass slider only — volume boost is applied as input gain, not EQ. */
    fun eqBassGainFraction(centerHz: Float, bassPercent: Int): Float {
        val bass = bassPercent.coerceIn(0, 100) / 100f
        return when {
            centerHz < 120f -> bass
            centerHz < 350f -> bass * 0.45f
            else -> 0f
        }
    }

    /** Fallback when DynamicsProcessing is unavailable: raise every band by the boost. */
    fun eqFallbackGainFraction(centerHz: Float, boostPercent: Int, bassPercent: Int): Float {
        return (eqBassGainFraction(centerHz, bassPercent) + boostPercent.coerceIn(0, 100) / 100f)
            .coerceAtMost(1f)
    }
}
