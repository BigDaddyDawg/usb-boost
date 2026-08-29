package com.usbboost.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostSettingsTest {
    @Test
    fun defaultIsOffSoAppCanOpenWithoutAService() {
        val settings = BoostSettings()
        assertFalse(settings.enabled)
        assertTrue(settings.legacyMode)
        assertFalse(settings.autoCarMode)
    }

    @Test
    fun boostDecibelsScalesToTwelveAtFull() {
        assertEquals(0f, BoostSettings(boostPercent = 0).boostDecibels(), 0.01f)
        assertEquals(6f, BoostSettings(boostPercent = 50).boostDecibels(), 0.01f)
        assertEquals(12f, BoostSettings(boostPercent = 100).boostDecibels(), 0.01f)
    }

    @Test
    fun millibelsMatchLoudnessEnhancerUnits() {
        assertEquals(0, BoostLogic.millibels(0))
        assertEquals(600, BoostLogic.millibels(50))
        assertEquals(1200, BoostLogic.millibels(100))
        assertEquals(1200, BoostLogic.millibels(999))
        assertEquals(0, BoostLogic.millibels(-10))
    }

    @Test
    fun bassStrengthStaysInAndroidRange() {
        assertEquals(0, BoostLogic.bassStrength(0))
        assertEquals(1000, BoostLogic.bassStrength(100))
        assertEquals(1000, BoostLogic.bassStrength(200))
    }

    @Test
    fun legacyModeAlwaysIncludesGlobalSession() {
        assertEquals(setOf(0), BoostLogic.sessionsToProcess(emptySet(), legacyMode = true))
        assertEquals(setOf(0, 12), BoostLogic.sessionsToProcess(setOf(12), legacyMode = true))
        assertEquals(setOf(12), BoostLogic.sessionsToProcess(setOf(12), legacyMode = false))
        assertTrue(BoostLogic.sessionsToProcess(emptySet(), legacyMode = false).isEmpty())
    }

    @Test
    fun effectsOnlyWhenEnabledAndInCarIfRequired() {
        assertFalse(BoostLogic.shouldApplyEffects(enabled = false, autoCarMode = true, carActive = true))
        assertFalse(BoostLogic.shouldApplyEffects(enabled = true, autoCarMode = true, carActive = false))
        assertTrue(BoostLogic.shouldApplyEffects(enabled = true, autoCarMode = true, carActive = true))
        assertTrue(BoostLogic.shouldApplyEffects(enabled = true, autoCarMode = false, carActive = false))
    }
}
