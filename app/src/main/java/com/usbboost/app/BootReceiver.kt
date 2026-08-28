package com.usbboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = BoostPrefs(context).load()
        if (prefs.enabled && prefs.startOnBoot) {
            BoostService.startSafely(context)
        }
    }
}
