package com.usbboost.app

object BoostLogic {
    fun millibels(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * BoostSettings.MAX_BOOST_DB * 100f).toInt()
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
     * The old code mapped onto [min, max], which turned a "boost" into a cut.
     */
    fun bandLevelMillibels(gainFraction: Float, min: Short, max: Short): Short {
        val level = (gainFraction.coerceIn(0f, 1f) * max).toInt()
        return level.coerceIn(min.toInt(), max.toInt()).toShort()
    }

    fun eqGainFraction(centerHz: Float, boostPercent: Int, bassPercent: Int): Float {
        val preamp = boostPercent.coerceIn(0, 100) / 100f
        val bass = bassPercent.coerceIn(0, 100) / 100f
        return when {
            centerHz < 120f -> (preamp + bass * 0.5f).coerceAtMost(1f)
            centerHz < 350f -> (preamp + bass * 0.25f).coerceAtMost(1f)
            else -> preamp
        }
    }
}
