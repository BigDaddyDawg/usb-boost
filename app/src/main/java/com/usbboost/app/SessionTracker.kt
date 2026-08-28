package com.usbboost.app

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern

class SessionTracker(
    private val context: Context,
    private val onSessionsChanged: (Set<Int>) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val knownSessions = ConcurrentHashMap.newKeySet<Int>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var legacyAttached = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val ids = configs.mapNotNull { extractSessionId(it) }.toSet()
            if (ids.isNotEmpty()) {
                updateSessions(ids)
            }
        }
    }

    fun start(settings: BoostSettings) {
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        refresh(settings)
    }

    fun stop() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        knownSessions.clear()
        legacyAttached = false
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
                knownSessions.clear()
                knownSessions.addAll(discovered)
                legacyAttached = settings.legacyMode
                notifyChange()
            }
        }
    }

    fun trackSession(sessionId: Int) {
        if (sessionId >= 0) {
            knownSessions.add(sessionId)
            notifyChange()
        }
    }

    fun untrackSession(sessionId: Int) {
        knownSessions.remove(sessionId)
        notifyChange()
    }

    fun activeSessions(): Set<Int> = knownSessions.toSet()

    private fun readActiveSessionsViaCallback(): Set<Int> {
        return runCatching {
            val method = AudioManager::class.java.getMethod("getActivePlaybackConfigurations")
            @Suppress("UNCHECKED_CAST")
            val configs = method.invoke(audioManager) as List<AudioPlaybackConfiguration>
            configs.mapNotNull { extractSessionId(it) }.toSet()
        }.getOrElse { emptySet() }
    }

    private fun readSessionsFromDump(): Set<Int> {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "media.audio_flinger"))
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            process.waitFor()
            SESSION_PATTERN.findAll(output).map { it.groupValues[1].toInt() }.toSet()
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

    private fun notifyChange() {
        onSessionsChanged(activeSessions())
    }

    companion object {
        private const val TAG = "SessionTracker"
        private val SESSION_PATTERN = Pattern.compile("session\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
    }
}
