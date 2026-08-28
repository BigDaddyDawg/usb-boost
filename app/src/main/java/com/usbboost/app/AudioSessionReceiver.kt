package com.usbboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId < 0) return

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                BoostService.start(context, sessionId)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                BoostService.start(context)
            }
        }
    }
}
