package com.usbboost.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
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
    fun usbCableConnected(context: Context): Boolean {
        return runCatching {
            context.getSystemService(UsbManager::class.java)?.deviceList?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    fun usbAccessoryConnected(context: Context): Boolean {
        return runCatching {
            context.getSystemService(UsbManager::class.java)?.accessoryList?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    fun current(context: Context): OutputState {
        return runCatching {
            val audioManager = context.getSystemService(AudioManager::class.java)
                ?: return OutputState(OutputKind.PHONE, "Phone speaker", false)
            val sinks = collectSinks(audioManager)
            CarOutput.classify(
                sinks,
                usbCableConnected(context),
                usbAccessoryConnected(context)
            )
        }.getOrElse {
            OutputState(OutputKind.PHONE, "Phone speaker", false)
        }
    }

    private fun collectSinks(audioManager: AudioManager): List<AudioSink> {
        val seen = linkedMapOf<Int, AudioSink>()
        fun addAll(devices: Array<out AudioDeviceInfo>?) {
            devices.orEmpty().forEach { device ->
                seen.putIfAbsent(
                    device.id,
                    AudioSink(
                        type = device.type,
                        name = device.productName?.toString().orEmpty()
                    )
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val media = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                addAll(audioManager.getAudioDevicesForAttributes(media).toTypedArray())
            }
        }
        addAll(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
        return seen.values.toList()
    }

    fun hasDumpPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED
    }
}
