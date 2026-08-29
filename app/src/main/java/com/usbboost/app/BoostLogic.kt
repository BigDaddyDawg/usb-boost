package com.usbboost.app

object BoostLogic {
    fun millibels(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * BoostSettings.MAX_BOOST_DB * 100f).toInt()
    }

    fun sessionsToProcess(discovered: Set<Int>, legacyMode: Boolean): Set<Int> {
        if (legacyMode) return discovered + 0
        return discovered.filter { it > 0 }.toSet()
    }

    fun shouldApplyEffects(enabled: Boolean, autoCarMode: Boolean, carActive: Boolean): Boolean {
        if (!enabled) return false
        return !autoCarMode || carActive
    }

    fun bassStrength(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * 1000f).toInt().coerceIn(0, 1000)
    }
}
