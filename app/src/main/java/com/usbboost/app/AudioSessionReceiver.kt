package com.usbboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log

class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId <= 0) return
        val app = context.applicationContext
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Open audio session $sessionId from ${intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)}")
                BoostEngine.noteSession(app, sessionId)
                if (BoostPrefs(app).load().enabled) {
                    runCatching { BoostEngine.start(app) }
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Close audio session $sessionId")
                BoostEngine.dropSession(app, sessionId)
            }
        }
    }

    companion object {
        private const val TAG = "AudioSessionReceiver"
    }
}
