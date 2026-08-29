package com.usbboost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class BoostService : Service() {

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        OutputWatcher.start(this)
        createChannel()
        if (!goForeground(BoostPrefs(this).load())) {
            isRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val settings = BoostPrefs(this).load()
                BoostPrefs(this).save(settings.copy(enabled = false))
                BoostEngine.stop()
                if (!settings.autoOnUsb) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_BOOST_UP -> bumpBoost(5)
            ACTION_BOOST_DOWN -> bumpBoost(-5)
            ACTION_LOCK -> MediaNudge.lockOn(this)
        }

        val settings = BoostPrefs(this).load()
        if (!goForeground(settings)) {
            BoostEngine.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!settings.enabled && !settings.autoOnUsb) {
            BoostEngine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (settings.enabled) {
            runCatching { BoostEngine.start(this) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { BoostEngine.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bumpBoost(delta: Int) {
        val settings = BoostPrefs(this).load()
        val car = OutputWatcher.carActive(this)
        val updated = settings.copy(
            boostPercent = BoostLogic.bumpBoost(settings.boostPercent, delta)
        ).writeBack(car)
        BoostPrefs(this).save(updated)
        if (updated.enabled) BoostEngine.start(this)
    }

    private fun goForeground(settings: BoostSettings): Boolean {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(settings), type)
        }.onFailure { error ->
            Log.e(TAG, "startForeground failed", error)
        }.isSuccess
    }

    private fun buildNotification(settings: BoostSettings): Notification {
        val open = pendingActivity(0)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val turnOff = pendingService(1, ACTION_TOGGLE, flags)
        val quieter = pendingService(2, ACTION_BOOST_DOWN, flags)
        val louder = pendingService(3, ACTION_BOOST_UP, flags)
        val lock = pendingService(4, ACTION_LOCK, flags)

        val attach = BoostEngine.currentStatus()
        val text = when {
            !settings.enabled && settings.autoOnUsb -> getString(R.string.status_waiting)
            !settings.enabled -> getString(R.string.status_off)
            attach.lockedOn -> getString(R.string.status_on_simple, settings.boostDecibels())
            else -> getString(R.string.boost_searching)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(settings.enabled || settings.autoOnUsb)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.quieter), quieter)
            .addAction(0, getString(R.string.louder), louder)
            .addAction(0, getString(R.string.lock_on), lock)
            .addAction(0, getString(R.string.turn_off), turnOff)
            .build()
    }

    private fun pendingActivity(request: Int): PendingIntent {
        return PendingIntent.getActivity(
            this,
            request,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pendingService(request: Int, action: String, flags: Int): PendingIntent {
        val intent = Intent(this, BoostService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, request, intent, flags)
        } else {
            PendingIntent.getService(this, request, intent, flags)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_TOGGLE = "com.usbboost.app.TOGGLE"
        const val ACTION_BOOST_UP = "com.usbboost.app.BOOST_UP"
        const val ACTION_BOOST_DOWN = "com.usbboost.app.BOOST_DOWN"
        const val ACTION_LOCK = "com.usbboost.app.LOCK"
        private const val CHANNEL_ID = "usb_boost_active"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BoostService"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun canStartForeground(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        fun startSafely(context: Context) {
            if (!canStartForeground(context)) {
                Log.w(TAG, "Skip keep-alive: notifications not granted")
                return
            }
            runCatching { context.startForegroundService(Intent(context, BoostService::class.java)) }
                .onFailure { Log.w(TAG, "Keep-alive service did not start", it) }
        }

        fun stop(context: Context) {
            BoostEngine.stop()
            runCatching { context.stopService(Intent(context, BoostService::class.java)) }
        }
    }
}
