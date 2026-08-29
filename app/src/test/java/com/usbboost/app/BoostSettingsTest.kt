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
        assertFalse(settings.autoOnUsb)
        assertEquals(SoundPreset.FLAT, settings.preset)
    }

    @Test
    fun boostDecibelsScaleWithMax() {
        assertEquals(0f, BoostSettings(boostPercent = 0).boostDecibels(), 0.01f)
        assertEquals(4.75f, BoostSettings(boostPercent = 50).boostDecibels(), 0.01f)
        assertEquals(9.5f, BoostSettings(boostPercent = 100).boostDecibels(), 0.01f)
    }

    @Test
    fun millibelsMatchSliderDecibels() {
        assertEquals(0, BoostLogic.millibels(0))
        assertEquals(475, BoostLogic.millibels(50))
        assertEquals(950, BoostLogic.millibels(100))
        assertEquals(950, BoostLogic.millibels(999))
        assertEquals(0, BoostLogic.millibels(-10))
    }

    @Test
    fun carMaximizerHitsTwoToThreeTimesLevel() {
        assertEquals(2f, BoostLogic.amplitudeMultiplier(BoostLogic.TWO_X_DB), 0.02f)
        assertEquals(3f, BoostLogic.amplitudeMultiplier(BoostLogic.THREE_X_DB), 0.05f)

        val off = BoostLogic.maximizerParams(0, carActive = true)
        assertEquals(0f, off.displayedDb, 0.01f)
        assertFalse(off.useDynamics)

        val twice = BoostLogic.maximizerParams(63, carActive = true)
        assertEquals(6f, twice.midPostGainDb, 0.2f)
        assertEquals(2f, BoostLogic.amplitudeMultiplier(twice.midPostGainDb), 0.08f)

        val full = BoostLogic.maximizerParams(100, carActive = true)
        assertTrue(full.useDynamics)
        assertEquals(9.5f, full.midPostGainDb, 0.01f)
        assertEquals(9.5f, full.displayedDb, 0.01f)
        assertEquals(3f, BoostLogic.amplitudeMultiplier(full.midPostGainDb), 0.05f)
        assertTrue(full.midPostGainDb > full.bassPostGainDb)
        assertTrue(full.mbcRatio <= 2.5f)
        assertEquals(0, full.loudnessEnhancerMb)
        assertEquals(950, BoostLogic.loudnessFallbackMb(100, carActive = true))

        val home = BoostLogic.maximizerParams(100, carActive = false)
        assertFalse(home.useDynamics)
        assertEquals(8f, home.displayedDb, 0.01f)
    }

    @Test
    fun bumpBoostStaysInRange() {
        assertEquals(70, BoostLogic.bumpBoost(65, 5))
        assertEquals(0, BoostLogic.bumpBoost(2, -10))
        assertEquals(100, BoostLogic.bumpBoost(99, 5))
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
    fun eqBandsAreToneOnlySoBoostCannotClipTheUsbDac() {
        val min: Short = -1500
        val max: Short = 1500
        val flat = EqBands()
        assertEquals(0, BoostLogic.eqBandMillibels(1000f, flat, min, max).toInt())
        assertEquals(0, BoostLogic.eqBandMillibels(8000f, flat, min, max).toInt())
        val bassHeavy = EqBands(bass = 6)
        assertEquals(600, BoostLogic.eqBandMillibels(80f, bassHeavy, min, max).toInt())
        assertEquals(0, BoostLogic.eqBandMillibels(3000f, bassHeavy, min, max).toInt())
        assertEquals(
            950,
            BoostLogic.eqBandMillibelsWithPreamp(1000f, 100, flat, min, max).toInt()
        )
    }

    @Test
    fun podcastPresetLiftsSpeechBands() {
        val podcast = EqShapes.forPreset(SoundPreset.PODCAST)
        assertTrue(podcast.mid > 0)
        assertTrue(podcast.presence > 0)
        assertTrue(podcast.bass < 0)
        assertEquals(EqBands(), EqShapes.forPreset(SoundPreset.FLAT))
    }

    @Test
    fun rockAndCountryAreNotFlat() {
        assertFalse(EqShapes.forPreset(SoundPreset.ROCK).isFlat())
        assertFalse(EqShapes.forPreset(SoundPreset.COUNTRY).isFlat())
        assertTrue(BoostLogic.eqWantsOn(0, EqShapes.forPreset(SoundPreset.ROCK)))
        assertFalse(BoostLogic.eqWantsOn(0, EqBands()))
    }

    @Test
    fun carProfileSwapKeepsHomeLevels() {
        val home = BoostSettings(boostPercent = 40, preset = SoundPreset.PODCAST, eq = EqShapes.forPreset(SoundPreset.PODCAST))
        val stored = home.writeBack(car = false).copy(boostPercent = 90, preset = SoundPreset.ROCK)
        val backHome = stored.applyProfile(car = false)
        assertEquals(40, backHome.boostPercent)
        assertEquals(SoundPreset.PODCAST, backHome.preset)
    }

    @Test
    fun boostDecibelsMatchTheSlider() {
        assertEquals(0f, BoostLogic.boostDecibels(0), 0.01f)
        assertEquals(6.175f, BoostLogic.boostDecibels(65), 0.01f)
        assertEquals(9.5f, BoostLogic.boostDecibels(100), 0.01f)
        assertEquals(9.5f, BoostLogic.appliedDecibels(100, carActive = true), 0.01f)
        assertEquals(8f, BoostLogic.appliedDecibels(100, carActive = false), 0.01f)
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
