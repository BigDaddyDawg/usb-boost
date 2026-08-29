package com.usbboost.app

import android.app.Application
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

class UsbBoostApp : Application() {
    private val sessionReceiver = AudioSessionReceiver()

    override fun onCreate() {
        super.onCreate()
        SessionRegistry.restore(this)
        registerSessionReceiver()
        OutputWatcher.start(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(filesDir, LAST_CRASH_FILE).writeText(
                    buildString {
                        appendLine(error::class.java.name)
                        appendLine(error.message ?: "")
                        appendLine(error.stackTraceToString())
                    }
                )
            }
            Log.e(TAG, "Uncaught crash", error)
            previous?.uncaughtException(thread, error)
        }
    }

    private fun registerSessionReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                sessionReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }.onFailure { Log.w(TAG, "Could not listen for music sessions", it) }
    }

    companion object {
        const val LAST_CRASH_FILE = "last-crash.txt"
        private const val TAG = "UsbBoostApp"
    }
}
