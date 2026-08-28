package com.usbboost.app

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

enum class OutputKind {
    PHONE,
    USB,
    BLUETOOTH,
    OTHER
}

data class OutputState(
    val kind: OutputKind,
    val label: String,
    val carLikely: Boolean
)

object OutputMonitor {
    fun current(context: Context): OutputState {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val usb = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        if (usb != null) {
            return OutputState(
                kind = OutputKind.USB,
                label = usb.productName?.toString() ?: "USB audio",
                carLikely = true
            )
        }

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE
        }
        if (wired != null) {
            return OutputState(
                kind = OutputKind.OTHER,
                label = wired.productName?.toString() ?: "Wired output",
                carLikely = true
            )
        }

        val bt = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        if (bt != null) {
            return OutputState(
                kind = OutputKind.BLUETOOTH,
                label = bt.productName?.toString() ?: "Bluetooth",
                carLikely = bt.productName?.toString()?.contains("car", ignoreCase = true) == true
            )
        }

        return OutputState(
            kind = OutputKind.PHONE,
            label = "Phone speaker",
            carLikely = false
        )
    }

    fun hasDumpPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED
    }
}
