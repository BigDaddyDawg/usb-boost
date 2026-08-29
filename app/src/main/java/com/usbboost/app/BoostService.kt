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
        createChannel()
        if (!goForeground(BoostPrefs(this).load())) {
            isRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = BoostPrefs(this).load()
        if (!goForeground(settings)) {
            BoostEngine.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        val turningOff = intent?.action == ACTION_TOGGLE || !settings.enabled
        if (turningOff) {
            if (intent?.action == ACTION_TOGGLE) {
                BoostPrefs(this).save(settings.copy(enabled = false))
            }
            BoostEngine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        runCatching { BoostEngine.start(this) }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { BoostEngine.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val turnOffIntent = Intent(this, BoostService::class.java).setAction(ACTION_TOGGLE)
        val turnOff = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this, 1, turnOffIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 1, turnOffIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val text = if (settings.enabled) {
            getString(R.string.status_on_simple, settings.boostDecibels())
        } else {
            getString(R.string.status_off)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(settings.enabled)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.turn_off), turnOff)
            .build()
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
