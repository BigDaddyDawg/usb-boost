package com.usbboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = BoostPrefs(context).load()
        val app = context.applicationContext
        if (prefs.enabled && prefs.startOnBoot) {
            BoostEngine.start(app)
            BoostService.startSafely(app)
        } else if (prefs.autoOnUsb) {
            BoostService.startSafely(app)
        }
    }
}
