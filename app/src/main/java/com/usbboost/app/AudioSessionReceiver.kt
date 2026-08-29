package com.usbboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BoostPrefs(context).load().enabled) return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId < 0) return
        // Never start a foreground service from here — that crashes on Pixel.
        runCatching { BoostEngine.start(context.applicationContext) }
    }
}
