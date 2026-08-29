package com.usbboost.app

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns audio effects. Lives on the application process so boost can work
 * even if the keep-alive service fails to start.
 */
object BoostEngine {
    private val chains = ConcurrentHashMap<Int, EffectChain>()
    private var tracker: SessionTracker? = null

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        val settings = BoostPrefs(app).load()
        val existing = tracker
        if (existing == null) {
            val created = SessionTracker(app) { sessions ->
                runCatching { applyToSessions(app, sessions) }
                    .onFailure { Log.w(TAG, "apply failed", it) }
            }
            tracker = created
            created.start(settings)
        } else {
            existing.start(settings)
        }
        applyToSessions(app, tracker?.activeSessions() ?: setOf(0))
    }

    @Synchronized
    fun stop() {
        tracker?.stop()
        chains.values.forEach { it.release() }
        chains.clear()
        tracker = null
    }

    private fun applyToSessions(context: Context, sessions: Set<Int>) {
        val settings = BoostPrefs(context).load()
        val output = OutputMonitor.current(context)
        val carActive = output.carLikely ||
            output.kind == OutputKind.USB ||
            OutputMonitor.usbCableConnected(context)

        val stale = chains.keys.filter { it !in sessions }
        stale.forEach { id -> chains.remove(id)?.release() }

        val targets = if (sessions.isEmpty() && settings.legacyMode) setOf(0) else sessions
        targets.forEach { sessionId ->
            val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
            chain.apply(settings, carActive)
        }
    }

    private const val TAG = "BoostEngine"
}
