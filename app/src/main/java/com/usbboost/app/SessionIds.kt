package com.usbboost.app

/**
 * Audio session IDs for other apps are not in the public SDK.
 * We recover them from effect-control broadcasts, dumpsys, and (rarely)
 * AudioPlaybackConfiguration.toString() when the system has not anonymized them.
 */
object SessionIds {
    private val DUMP = Regex("""(?i)session(?:[\s_]*id)?[\s:=]+(\d+)""")
    private val TO_STRING = Regex("""(?i)sessionId\s*[:=]\s*(\d+)""")

    fun fromDump(text: String): Set<Int> {
        if (text.isBlank() || text.contains("Permission Denial", ignoreCase = true)) {
            return emptySet()
        }
        return DUMP.findAll(text)
            .map { it.groupValues[1].toInt() }
            .filter { it > 0 }
            .toSet()
    }

    fun fromPlaybackToString(text: String): Int? {
        return TO_STRING.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    }
}
