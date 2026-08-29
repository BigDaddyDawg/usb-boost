package com.usbboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class UsbAutoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != UsbManager.ACTION_USB_DEVICE_ATTACHED &&
            action != UsbManager.ACTION_USB_DEVICE_DETACHED &&
            action != UsbManager.ACTION_USB_ACCESSORY_ATTACHED &&
            action != UsbManager.ACTION_USB_ACCESSORY_DETACHED
        ) {
            return
        }
        val app = context.applicationContext
        OutputWatcher.start(app)
        val settings = BoostPrefs(app).load()
        if (!settings.autoOnUsb) return
        val car = OutputWatcher.carActive(app)
        val updated = settings.writeBack(!car).applyProfile(car).copy(enabled = car)
        BoostPrefs(app).save(updated)
        if (updated.enabled) {
            BoostEngine.start(app)
            BoostService.startSafely(app)
        } else {
            BoostEngine.stop()
            BoostService.startSafely(app)
        }
    }
}
