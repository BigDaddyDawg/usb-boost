package com.usbboost.app

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

object OutputWatcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var lastCar: Boolean? = null
    private var started = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            handleChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            handleChange()
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!started) {
            val am = app.getSystemService(AudioManager::class.java)
            runCatching { am?.registerAudioDeviceCallback(callback, mainHandler) }
            started = true
        }
        if (lastCar == null) {
            lastCar = carActive(app)
        }
    }

    fun carActive(context: Context): Boolean {
        val output = OutputMonitor.current(context)
        return output.carLikely ||
            output.kind == OutputKind.USB ||
            OutputMonitor.usbCableConnected(context)
    }

    private fun handleChange() {
        val context = appContext ?: return
        val car = carActive(context)
        val previous = lastCar
        if (previous == null) {
            lastCar = car
            return
        }
        if (car == previous) return
        lastCar = car

        val prefs = BoostPrefs(context)
        var settings = prefs.load().writeBack(!car).applyProfile(car)
        if (settings.autoOnUsb) {
            settings = settings.copy(enabled = car)
        }
        prefs.save(settings)
        if (settings.enabled) {
            BoostEngine.start(context)
            BoostService.startSafely(context)
        } else if (settings.autoOnUsb) {
            BoostEngine.stop()
            BoostService.startSafely(context)
        } else {
            BoostService.stop(context)
        }
    }
}
