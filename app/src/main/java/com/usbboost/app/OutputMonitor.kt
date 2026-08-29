package com.usbboost.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager

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
    fun usbCableConnected(context: Context): Boolean {
        return runCatching {
            context.getSystemService(UsbManager::class.java)?.deviceList?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    fun current(context: Context): OutputState {
        return runCatching {
            val audioManager = context.getSystemService(AudioManager::class.java)
                ?: return OutputState(OutputKind.PHONE, "Phone speaker", false)
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbCable = usbCableConnected(context)

            val usb = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            if (usb != null || usbCable) {
                return OutputState(
                    kind = OutputKind.USB,
                    label = usb?.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB / Android Auto",
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
                    label = wired.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired output",
                    carLikely = true
                )
            }

            val bt = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            if (bt != null) {
                val name = bt.productName?.toString().orEmpty()
                return OutputState(
                    kind = OutputKind.BLUETOOTH,
                    label = name.ifBlank { "Bluetooth" },
                    carLikely = name.contains("car", ignoreCase = true)
                )
            }

            OutputState(OutputKind.PHONE, "Phone speaker", false)
        }.getOrElse {
            OutputState(OutputKind.PHONE, "Phone speaker", false)
        }
    }

    fun hasDumpPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED
    }
}
