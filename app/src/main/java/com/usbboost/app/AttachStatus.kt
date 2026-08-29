package com.usbboost.app

data class AttachStatus(
    val enabled: Boolean = false,
    val applying: Boolean = false,
    val musicPlaying: Boolean = false,
    val trustedSessions: Set<Int> = emptySet(),
    val attachedSessions: Set<Int> = emptySet()
) {
    val lockedOn: Boolean get() = attachedSessions.any { it > 0 }
}
