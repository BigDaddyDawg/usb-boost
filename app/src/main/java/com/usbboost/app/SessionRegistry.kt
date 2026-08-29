package com.usbboost.app

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object SessionRegistry {
    private const val PREFS = "usb_boost_sessions"
    private const val KEY_IDS = "ids"
    private const val MAX_SESSIONS = 32

    private val live = ConcurrentHashMap<Int, Long>()

    @Synchronized
    fun restore(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IDS, "")
            .orEmpty()
        raw.split(',')
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
            .forEach { live.putIfAbsent(it, 0L) }
    }

    fun remember(context: Context, id: Int) {
        if (id <= 0) return
        live[id] = System.currentTimeMillis()
        trim()
        persist(context)
    }

    fun forget(context: Context, id: Int) {
        if (live.remove(id) != null) persist(context)
    }

    fun snapshot(): Set<Int> = live.keys.toSet()

    fun hasRealSession(): Boolean = live.keys.any { it > 0 }

    private fun trim() {
        if (live.size <= MAX_SESSIONS) return
        val extra = live.entries.sortedBy { it.value }.take(live.size - MAX_SESSIONS)
        extra.forEach { live.remove(it.key, it.value) }
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IDS, live.keys.joinToString(","))
            .apply()
    }
}
