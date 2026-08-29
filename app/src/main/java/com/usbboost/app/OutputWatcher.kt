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
        val car = carActive(app)
        if (lastCar == null) {
            lastCar = car
            MediaVolume.sync(app, car)
            return
        }
        if (car != lastCar) {
            handleChange()
        }
    }

    fun carActive(context: Context): Boolean = OutputMonitor.current(context).carLikely

    private fun handleChange() {
        val context = appContext ?: return
        val car = carActive(context)
        val previous = lastCar
        if (previous == null) {
            lastCar = car
            MediaVolume.sync(context, car)
            return
        }
        if (car == previous) return
        lastCar = car
        MediaVolume.sync(context, car)

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

/**
 * USB DACs often honour STREAM_MUSIC. Raise it to max while the car is connected
 * so the digital output is as hot as Android allows, then restore.
 */
object MediaVolume {
    @Volatile
    private var savedVolume: Int? = null

    fun sync(context: Context, car: Boolean) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        if (am.isVolumeFixed) return
        val stream = AudioManager.STREAM_MUSIC
        runCatching {
            if (car) {
                if (savedVolume == null) {
                    savedVolume = am.getStreamVolume(stream)
                }
                val max = am.getStreamMaxVolume(stream)
                if (am.getStreamVolume(stream) < max) {
                    am.setStreamVolume(stream, max, 0)
                }
            } else {
                val restore = savedVolume ?: return
                savedVolume = null
                am.setStreamVolume(
                    stream,
                    restore.coerceIn(0, am.getStreamMaxVolume(stream)),
                    0
                )
            }
        }
    }
}
