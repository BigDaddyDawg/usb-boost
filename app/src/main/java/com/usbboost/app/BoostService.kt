package com.usbboost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap

class BoostService : Service() {
    private val prefs by lazy { BoostPrefs(this) }
    private val chains = ConcurrentHashMap<Int, EffectChain>()
    private var outputState = OutputMonitor.current(this)
    private lateinit var sessionTracker: SessionTracker

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) = refreshOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) = refreshOutput()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        sessionTracker = SessionTracker(this) { sessions -> applyToSessions(sessions) }
        getSystemService(AudioManager::class.java).registerAudioDeviceCallback(deviceCallback, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getIntExtra(EXTRA_SESSION_ID, -1)?.takeIf { it >= 0 }?.let {
            sessionTracker.trackSession(it)
        }

        val settings = prefs.load()
        startForeground(NOTIFICATION_ID, buildNotification(settings, outputState))

        if (intent?.action == ACTION_RELOAD) {
            reloadFromUi()
            return START_STICKY
        }

        sessionTracker.start(settings)
        sessionTracker.refresh(settings)
        refreshOutput()
        return START_STICKY
    }

    override fun onDestroy() {
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(deviceCallback)
        sessionTracker.stop()
        chains.values.forEach { it.release() }
        chains.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun reloadFromUi() {
        val settings = prefs.load()
        sessionTracker.refresh(settings)
        refreshOutput()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(settings, outputState))
    }

    private fun refreshOutput() {
        outputState = OutputMonitor.current(this)
        applyToSessions(sessionTracker.activeSessions())
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(prefs.load(), outputState))
    }

    private fun applyToSessions(sessions: Set<Int>) {
        val settings = prefs.load()
        val carActive = outputState.carLikely || outputState.kind == OutputKind.USB

        val stale = chains.keys.filter { it !in sessions }
        stale.forEach { id ->
            chains.remove(id)?.release()
        }

        sessions.forEach { sessionId ->
            val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
            chain.apply(settings, carActive)
        }
    }

    private fun buildNotification(settings: BoostSettings, output: OutputState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = when {
            !settings.enabled -> "Boost paused"
            settings.autoCarMode && !output.carLikely && output.kind != OutputKind.USB -> "Waiting for car/USB"
            else -> "Boosting ${output.label}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_RELOAD = "com.usbboost.app.RELOAD"
        const val EXTRA_SESSION_ID = "session_id"
        private const val CHANNEL_ID = "usb_boost_active"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, sessionId: Int? = null) {
            val intent = Intent(context, BoostService::class.java)
            sessionId?.let { intent.putExtra(EXTRA_SESSION_ID, it) }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BoostService::class.java))
        }
    }
}
