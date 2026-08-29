package com.usbboost.app

import android.app.Application
import android.util.Log
import java.io.File

class UsbBoostApp : Application() {
    override fun onCreate() {
        super.onCreate()
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

    companion object {
        const val LAST_CRASH_FILE = "last-crash.txt"
        private const val TAG = "UsbBoostApp"
    }
}
