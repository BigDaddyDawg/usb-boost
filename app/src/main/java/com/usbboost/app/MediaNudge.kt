package com.usbboost.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

object MediaNudge {
    fun lockOn(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        fun send(code: Int) {
            runCatching {
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
        send(KeyEvent.KEYCODE_MEDIA_PAUSE)
        Handler(Looper.getMainLooper()).postDelayed({
            send(KeyEvent.KEYCODE_MEDIA_PLAY)
            if (BoostPrefs(context).load().enabled) {
                BoostEngine.start(context.applicationContext)
            }
        }, 280)
    }
}
