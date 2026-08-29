package com.usbboost.app

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class SessionTracker(
    private val context: Context,
    private val onSessionsChanged: (Set<Int>) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val knownSessions = ConcurrentHashMap.newKeySet<Int>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val ids = configs.mapNotNull { extractSessionId(it) }.toSet()
            if (ids.isNotEmpty()) {
                updateSessions(ids)
            }
        }
    }

    fun start(settings: BoostSettings) {
        val am = audioManager ?: return
        runCatching { am.unregisterAudioPlaybackCallback(playbackCallback) }
        runCatching { am.registerAudioPlaybackCallback(playbackCallback, mainHandler) }
        schedulePoll()
        refresh(settings)
    }

    fun stop() {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        runCatching { audioManager?.unregisterAudioPlaybackCallback(playbackCallback) }
        knownSessions.clear()
        notifyChange()
    }

    fun refresh(settings: BoostSettings) {
        executor.execute {
            val discovered = linkedSetOf<Int>()
            discovered.addAll(readActiveSessionsViaCallback())
            if (settings.enhancedDetection && OutputMonitor.hasDumpPermission(context)) {
                discovered.addAll(readSessionsFromDump())
            }
            if (settings.legacyMode) {
                discovered.add(0)
            }
            mainHandler.post {
                if (discovered.isNotEmpty()) {
                    knownSessions.addAll(discovered)
                }
                if (settings.legacyMode) {
                    knownSessions.add(0)
                } else {
                    knownSessions.remove(0)
                }
                notifyChange()
            }
        }
    }

    fun activeSessions(): Set<Int> = knownSessions.toSet()

    private fun readActiveSessionsViaCallback(): Set<Int> {
        val am = audioManager ?: return emptySet()
        return runCatching {
            val method = AudioManager::class.java.getMethod("getActivePlaybackConfigurations")
            @Suppress("UNCHECKED_CAST")
            val configs = method.invoke(am) as List<AudioPlaybackConfiguration>
            configs.mapNotNull { extractSessionId(it) }.toSet()
        }.getOrElse { emptySet() }
    }

    private fun readSessionsFromDump(): Set<Int> {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "media.audio_flinger"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            SESSION_REGEX.findAll(output)
                .map { it.groupValues[1].toInt() }
                .toSet()
        }.getOrElse {
            Log.w(TAG, "Enhanced session detection failed", it)
            emptySet()
        }
    }

    private fun extractSessionId(config: AudioPlaybackConfiguration): Int? {
        return runCatching {
            val method = AudioPlaybackConfiguration::class.java.getMethod("getSessionId")
            val id = method.invoke(config) as Int
            if (id > 0) id else null
        }.getOrNull()
    }

    private fun updateSessions(ids: Set<Int>) {
        var changed = false
        ids.forEach {
            if (knownSessions.add(it)) changed = true
        }
        if (changed) notifyChange()
    }

    private fun schedulePoll() {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                refresh(BoostPrefs(context).load())
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        mainHandler.postDelayed(runnable, POLL_INTERVAL_MS)
    }

    private fun notifyChange() {
        onSessionsChanged(activeSessions())
    }

    companion object {
        private const val TAG = "SessionTracker"
        private const val POLL_INTERVAL_MS = 2500L
        private val SESSION_REGEX = Regex("session\\s+(\\d+)", RegexOption.IGNORE_CASE)
    }
}
