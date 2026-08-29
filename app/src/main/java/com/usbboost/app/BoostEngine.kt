package com.usbboost.app

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object BoostEngine {
    private val chains = ConcurrentHashMap<Int, EffectChain>()
    private var tracker: SessionTracker? = null
    private val running = AtomicBoolean(false)
    private val status = AtomicReference(AttachStatus())

    fun currentStatus(): AttachStatus = status.get()

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        OutputWatcher.start(app)
        SessionRegistry.restore(app)
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
        status.set(AttachStatus())
    }

    @Synchronized
    fun noteSession(context: Context, sessionId: Int) {
        if (sessionId <= 0) return
        SessionRegistry.remember(context.applicationContext, sessionId)
        tracker?.remember(sessionId)
        if (running.get()) {
            applyToSessions(context.applicationContext, tracker?.activeSessions().orEmpty() + sessionId)
        }
    }

    @Synchronized
    fun dropSession(context: Context, sessionId: Int) {
        SessionRegistry.forget(context.applicationContext, sessionId)
        tracker?.forget(sessionId)
        chains.remove(sessionId)?.release()
        if (running.get()) {
            applyToSessions(context.applicationContext, tracker?.activeSessions().orEmpty())
        }
    }

    @Synchronized
    private fun applyToSessions(context: Context, sessions: Set<Int>) {
        if (!running.get()) return
        val settings = BoostPrefs(context).load()
        val output = OutputMonitor.current(context)
        val carActive = output.carLikely ||
            output.kind == OutputKind.USB ||
            OutputMonitor.usbCableConnected(context)
        val applying = BoostLogic.shouldApplyEffects(settings.enabled, settings.autoCarMode, carActive)
        val trusted = SessionRegistry.snapshot()
        val targets = BoostLogic.sessionsToProcess(sessions + trusted, settings.legacyMode)

        chains.keys.filter { it !in targets }.forEach { id ->
            chains.remove(id)?.release()
        }
        val attached = linkedSetOf<Int>()
        targets.forEach { sessionId ->
            val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
            if (chain.apply(settings, carActive) && BoostLogic.isRealSession(sessionId)) {
                attached.add(sessionId)
            }
        }
        status.set(
            AttachStatus(
                enabled = settings.enabled,
                applying = applying,
                musicPlaying = SessionTracker.musicPlaying(context),
                trustedSessions = trusted,
                attachedSessions = attached
            )
        )
        Log.i(
            TAG,
            "boost enabled=${settings.enabled} applying=$applying attached=$attached trusted=$trusted"
        )
    }

    private const val TAG = "BoostEngine"
}
