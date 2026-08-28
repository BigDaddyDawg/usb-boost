package com.usbboost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.ConcurrentHashMap

class BoostService : Service() {
    private val prefs by lazy { BoostPrefs(this) }
    private val chains = ConcurrentHashMap<Int, EffectChain>()
    private var outputState = OutputMonitor.current(this)
    private lateinit var sessionTracker: SessionTracker
    private var mediaSession: MediaSession? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) = refreshOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) = refreshOutput()
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        mediaSession = runCatching { createMediaSession() }.getOrNull()
        sessionTracker = SessionTracker(this) { sessions ->
            runCatching { applyToSessions(sessions) }
                .onFailure { Log.w(TAG, "applyToSessions failed", it) }
        }
        runCatching {
            getSystemService(AudioManager::class.java).registerAudioDeviceCallback(deviceCallback, null)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = prefs.load()
        goForeground(settings)

        if (intent?.action == ACTION_TOGGLE) {
            prefs.save(settings.copy(enabled = false))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!settings.enabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.getIntExtra(EXTRA_SESSION_ID, -1)?.takeIf { it >= 0 }?.let {
            sessionTracker.trackSession(it)
        }
        intent?.getIntExtra(EXTRA_UNTRACK_SESSION, -1)?.takeIf { it >= 0 }?.let {
            sessionTracker.untrackSession(it)
        }

        runCatching { sessionTracker.start(settings) }
        runCatching { refreshOutput() }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching {
            getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(deviceCallback)
        }
        runCatching { sessionTracker.stop() }
        chains.values.forEach { it.release() }
        chains.clear()
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground(settings: BoostSettings) {
        val notification = buildNotification(settings, outputState)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }.onFailure {
            Log.e(TAG, "startForeground failed", it)
            stopSelf()
        }
    }

    private fun createMediaSession(): MediaSession {
        return MediaSession(this, "usb-boost").apply {
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY)
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            isActive = true
        }
    }

    private fun refreshOutput() {
        outputState = OutputMonitor.current(this)
        applyToSessions(sessionTracker.activeSessions())
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(prefs.load(), outputState))
    }

    private fun applyToSessions(sessions: Set<Int>) {
        val settings = prefs.load()
        val carActive = outputState.carLikely ||
            outputState.kind == OutputKind.USB ||
            OutputMonitor.usbCableConnected(this)

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
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, BoostService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = when {
            !settings.enabled -> getString(R.string.status_off)
            settings.autoCarMode && !output.carLikely && output.kind != OutputKind.USB ->
                getString(R.string.status_waiting)
            else -> getString(R.string.status_on, settings.boostDecibels(), output.label)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(settings.enabled)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.turn_off), toggle)
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
        const val ACTION_RELOAD = "com.usbboost.app.RELOAD"
        const val ACTION_TOGGLE = "com.usbboost.app.TOGGLE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_UNTRACK_SESSION = "untrack_session"
        private const val CHANNEL_ID = "usb_boost_active"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BoostService"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startSafely(context: Context, sessionId: Int? = null, untrack: Int? = null) {
            val intent = Intent(context, BoostService::class.java)
            sessionId?.let { intent.putExtra(EXTRA_SESSION_ID, it) }
            untrack?.let { intent.putExtra(EXTRA_UNTRACK_SESSION, it) }
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                Log.w(TAG, "Could not start boost service", it)
            }
        }

        /** Only ping an already-running service (safe from background broadcasts). */
        fun notifyIfRunning(context: Context, sessionId: Int? = null, untrack: Int? = null) {
            if (!isRunning) return
            val intent = Intent(context, BoostService::class.java)
            sessionId?.let { intent.putExtra(EXTRA_SESSION_ID, it) }
            untrack?.let { intent.putExtra(EXTRA_UNTRACK_SESSION, it) }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Could not update running service", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, BoostService::class.java)) }
        }
    }
}
