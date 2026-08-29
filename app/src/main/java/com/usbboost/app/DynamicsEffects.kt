package com.usbboost.app

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log

/**
 * Loudness maximizer: light input gain → vocal presence EQ → 3-band compressor
 * with make-up (mids highest) → peak limiter.
 * Isolated so EffectChain can load on API 26–27.
 */
internal object DynamicsEffects {
    private const val BASS_HZ = 180f
    private const val MID_HZ = 4500f
    private const val HIGH_HZ = 20000f
    private const val PRESENCE_HZ = 2500f
    private const val AIR_HZ = 12000f

    fun create(sessionId: Int, priority: Int): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        for (channels in intArrayOf(2, 1)) {
            val created = runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    channels,
                    true,
                    3,
                    true,
                    3,
                    false,
                    0,
                    true
                ).build()
                DynamicsProcessing(priority, sessionId, config)
            }.onFailure {
                Log.w(TAG, "DynamicsProcessing($channels ch) failed for session $sessionId", it)
            }.getOrNull()
            if (created != null) return created
        }
        return null
    }

    fun apply(effect: Any?, params: MaximizerParams): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val dp = effect as? DynamicsProcessing ?: return false
        if (!params.useDynamics || !params.enabled) {
            runCatching { dp.enabled = false }
            return false
        }
        return runCatching {
            dp.setInputGainAllChannelsTo(params.inputGainDb)
            val presenceOk = applyPresence(dp, params.presenceDb)
            val mbcOk = applyMbc(dp, params)
            val limiterPost = if (mbcOk) params.limiterPostGainDb else params.midPostGainDb
            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    0.8f,
                    40f,
                    10f,
                    params.limiterThresholdDb,
                    limiterPost
                )
            )
            if (!presenceOk) {
                Log.w(TAG, "Presence EQ skipped; maximizer still running")
            }
            dp.enabled = true
            dp.enabled
        }.onFailure {
            Log.w(TAG, "Maximizer update failed", it)
        }.getOrDefault(false)
    }

    private fun applyPresence(dp: DynamicsProcessing, presenceDb: Float): Boolean {
        return runCatching {
            dp.setPreEqBandAllChannelsTo(0, DynamicsProcessing.EqBand(true, 200f, 0f))
            dp.setPreEqBandAllChannelsTo(
                1,
                DynamicsProcessing.EqBand(true, PRESENCE_HZ, presenceDb)
            )
            dp.setPreEqBandAllChannelsTo(
                2,
                DynamicsProcessing.EqBand(true, AIR_HZ, presenceDb * 0.35f)
            )
            true
        }.onFailure {
            Log.w(TAG, "Presence EQ failed", it)
        }.getOrDefault(false)
    }

    private fun applyMbc(dp: DynamicsProcessing, params: MaximizerParams): Boolean {
        return runCatching {
            dp.setMbcBandAllChannelsTo(0, mbcBand(BASS_HZ, 15f, 120f, params, params.bassPostGainDb))
            dp.setMbcBandAllChannelsTo(1, mbcBand(MID_HZ, 8f, 70f, params, params.midPostGainDb))
            dp.setMbcBandAllChannelsTo(2, mbcBand(HIGH_HZ, 4f, 45f, params, params.highPostGainDb))
            true
        }.onFailure {
            Log.w(TAG, "MBC failed", it)
        }.getOrDefault(false)
    }

    private fun mbcBand(
        cutoffHz: Float,
        attackMs: Float,
        releaseMs: Float,
        params: MaximizerParams,
        postGainDb: Float
    ): DynamicsProcessing.MbcBand {
        return DynamicsProcessing.MbcBand(
            true,
            cutoffHz,
            attackMs,
            releaseMs,
            params.mbcRatio,
            params.mbcThresholdDb,
            3f,
            -90f,
            1f,
            0f,
            postGainDb
        )
    }

    fun isEnabled(effect: Any?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return (effect as? DynamicsProcessing)?.enabled == true
    }

    fun release(effect: Any?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val dp = effect as? DynamicsProcessing ?: return
        runCatching {
            dp.enabled = false
            dp.release()
        }
    }

    private const val TAG = "DynamicsEffects"
}
