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
    fun legacyModeOnlyUsesGlobalSessionWhenNothingRealWasFound() {
        assertEquals(setOf(0), BoostLogic.sessionsToProcess(emptySet(), legacyMode = true))
        assertEquals(setOf(12), BoostLogic.sessionsToProcess(setOf(12), legacyMode = true))
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

    @Test
    fun eqBandLevelBoostsFromUnityInsteadOfCutting() {
        val min: Short = -1500
        val max: Short = 1500
        assertEquals(0, BoostLogic.bandLevelMillibels(0f, min, max).toInt())
        assertEquals(750, BoostLogic.bandLevelMillibels(0.5f, min, max).toInt())
        assertEquals(1500, BoostLogic.bandLevelMillibels(1f, min, max).toInt())
        assertTrue(BoostLogic.bandLevelMillibels(0.35f, min, max) > 0)
    }

    @Test
    fun eqBassDoesNotTouchMidsAndHighs() {
        assertEquals(0f, BoostLogic.eqBassGainFraction(1000f, bassPercent = 80), 0.01f)
        assertEquals(0f, BoostLogic.eqBassGainFraction(8000f, bassPercent = 100), 0.01f)
        assertEquals(1f, BoostLogic.eqBassGainFraction(80f, bassPercent = 100), 0.01f)
        assertTrue(BoostLogic.eqBassGainFraction(200f, bassPercent = 100) > 0f)
        assertTrue(BoostLogic.eqBassGainFraction(200f, bassPercent = 100) < 1f)
    }

    @Test
    fun boostDecibelsMatchTheSlider() {
        assertEquals(0f, BoostLogic.boostDecibels(0), 0.01f)
        assertEquals(7.8f, BoostLogic.boostDecibels(65), 0.01f)
        assertEquals(12f, BoostLogic.boostDecibels(100), 0.01f)
    }

    @Test
    fun dumpParserFindsRealSessionsAndIgnoresZeroAndPermissionDenied() {
        val dump = """
            Output thread 0xb400007a:
              Track id 5:
                session  847
              Track id 6:
                session id: 0
            AudioPlaybackConfiguration piid:12 sessionId:1901 state:started
        """.trimIndent()
        assertEquals(setOf(847, 1901), SessionIds.fromDump(dump))
        assertTrue(SessionIds.fromDump("Permission Denial: can't dump media.audio_flinger").isEmpty())
        assertEquals(1901, SessionIds.fromPlaybackToString("AudioPlaybackConfiguration piid:12 sessionId:1901 state:started"))
        assertEquals(null, SessionIds.fromPlaybackToString("AudioPlaybackConfiguration piid:12 sessionId:0 state:started"))
    }

    @Test
    fun sessionZeroIsNotARealAttachTarget() {
        assertFalse(BoostLogic.isRealSession(0))
        assertTrue(BoostLogic.isRealSession(847))
    }

    @Test
    fun attachStatusLockedOnNeedsARealSession() {
        assertFalse(AttachStatus(attachedSessions = setOf(0)).lockedOn)
        assertTrue(AttachStatus(attachedSessions = setOf(847)).lockedOn)
    }
}
