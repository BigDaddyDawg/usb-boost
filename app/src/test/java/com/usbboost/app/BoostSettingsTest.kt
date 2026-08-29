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
    }

    @Test
    fun boostDecibelsScalesToTwelveAtFull() {
        assertEquals(0f, BoostSettings(boostPercent = 0).boostDecibels(), 0.01f)
        assertEquals(6f, BoostSettings(boostPercent = 50).boostDecibels(), 0.01f)
        assertEquals(12f, BoostSettings(boostPercent = 100).boostDecibels(), 0.01f)
    }

    @Test
    fun percentToMillibelsMatchesLoudnessEnhancerUnits() {
        // 100% = 12 dB = 1200 millibels
        val percent = 100
        val millibels = ((percent / 100f) * BoostSettings.MAX_BOOST_DB * 100f).toInt()
        assertEquals(1200, millibels)
    }
}
