package com.usbboost.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

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
            if (stopped) return
            val ids = configs.mapNotNull { extractSessionId(it) }.toSet()
            if (ids.isNotEmpty()) {
                updateSessions(ids)
            }
        }
    }

    private var stopped = false

    fun start(settings: BoostSettings) {
        stopped = false
        val am = audioManager ?: return
        runCatching { am.unregisterAudioPlaybackCallback(playbackCallback) }
        runCatching { am.registerAudioPlaybackCallback(playbackCallback, mainHandler) }
        knownSessions.addAll(SessionRegistry.snapshot())
        schedulePoll()
        refresh(settings)
    }

    fun stop() {
        stopped = true
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        runCatching { audioManager?.unregisterAudioPlaybackCallback(playbackCallback) }
        executor.shutdownNow()
        knownSessions.clear()
    }

    fun refresh(settings: BoostSettings) {
        if (stopped) return
        try {
            executor.execute {
                if (stopped) return@execute
                val discovered = linkedSetOf<Int>()
                discovered.addAll(SessionRegistry.snapshot())
                discovered.addAll(readActiveSessionsViaCallback())
                if (settings.enhancedDetection) {
                    discovered.addAll(readSessionsFromDump())
                }
                if (settings.legacyMode) {
                    discovered.add(0)
                }
                mainHandler.post {
                    if (stopped) return@post
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
        } catch (_: RejectedExecutionException) {
            Log.w(TAG, "Session refresh skipped; tracker is stopping")
        }
    }

    fun activeSessions(): Set<Int> = knownSessions.toSet()

    fun remember(sessionId: Int) {
        if (sessionId <= 0) return
        if (knownSessions.add(sessionId)) notifyChange()
    }

    fun forget(sessionId: Int) {
        if (knownSessions.remove(sessionId)) notifyChange()
    }

    private fun readActiveSessionsViaCallback(): Set<Int> {
        val am = audioManager ?: return emptySet()
        return runCatching {
            am.activePlaybackConfigurations.mapNotNull { extractSessionId(it) }.toSet()
        }.getOrElse { emptySet() }
    }

    private fun readSessionsFromDump(): Set<Int> {
        val ids = linkedSetOf<Int>()
        DUMP_COMMANDS.forEach { command ->
            runCatching {
                val process = Runtime.getRuntime().exec(command)
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroy()
                }
                ids.addAll(SessionIds.fromDump(output))
            }.onFailure {
                Log.w(TAG, "Session dump failed for ${command.joinToString(" ")}", it)
            }
        }
        return ids
    }

    private fun extractSessionId(config: AudioPlaybackConfiguration): Int? {
        SessionIds.fromPlaybackToString(config.toString())?.let { return it }
        return runCatching {
            val method = AudioPlaybackConfiguration::class.java.getDeclaredMethod("getSessionId")
            method.isAccessible = true
            val id = method.invoke(config) as Int
            id.takeIf { it > 0 }
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
                if (stopped) return
                refresh(BoostPrefs(context).load())
                if (!stopped) mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun notifyChange() {
        onSessionsChanged(activeSessions())
    }

    companion object {
        private const val TAG = "SessionTracker"
        private const val POLL_INTERVAL_MS = 1500L
        private val DUMP_COMMANDS = listOf(
            arrayOf("dumpsys", "media.audio_flinger"),
            arrayOf("dumpsys", "audio")
        )

        fun musicPlaying(context: Context): Boolean {
            val am = context.getSystemService(AudioManager::class.java) ?: return false
            return runCatching {
                am.activePlaybackConfigurations.any { config ->
                    val usage = config.audioAttributes.usage
                    usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME
                }
            }.getOrDefault(false)
        }
    }
}
