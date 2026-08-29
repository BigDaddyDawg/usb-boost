package com.usbboost.app

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object BoostEngine {
    private val chains = ConcurrentHashMap<Int, EffectChain>()
    private var tracker: SessionTracker? = null
    private val running = AtomicBoolean(false)

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        running.set(true)
        val settings = BoostPrefs(app).load()
        val existing = tracker
        if (existing == null) {
            val created = SessionTracker(app) { sessions ->
                if (!running.get()) return@SessionTracker
                runCatching { applyToSessions(app, sessions) }
                    .onFailure { Log.w(TAG, "apply failed", it) }
            }
            tracker = created
            created.start(settings)
        } else {
            existing.start(settings)
        }
        applyToSessions(app, tracker?.activeSessions().orEmpty())
    }

    @Synchronized
    fun stop() {
        running.set(false)
        tracker?.stop()
        tracker = null
        chains.values.forEach { it.release() }
        chains.clear()
    }

    private fun applyToSessions(context: Context, sessions: Set<Int>) {
        if (!running.get()) return
        val settings = BoostPrefs(context).load()
        val output = OutputMonitor.current(context)
        val carActive = output.carLikely ||
            output.kind == OutputKind.USB ||
            OutputMonitor.usbCableConnected(context)
        val targets = BoostLogic.sessionsToProcess(sessions, settings.legacyMode)

        chains.keys.filter { it !in targets }.forEach { id ->
            chains.remove(id)?.release()
        }
        targets.forEach { sessionId ->
            val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
            chain.apply(settings, carActive)
        }
    }

    private const val TAG = "BoostEngine"
}
