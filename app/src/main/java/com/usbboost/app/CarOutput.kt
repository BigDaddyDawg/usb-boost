package com.usbboost.app

/**
 * Pure classification of audio sinks. Integer [type] values match
 * [android.media.AudioDeviceInfo] so JVM tests can cover IntelliLink / Android Auto
 * without the Android runtime.
 */
data class AudioSink(
    val type: Int,
    val name: String
)

object CarOutput {
    // AudioDeviceInfo type constants (AOSP).
    const val TYPE_BUILTIN_EARPIECE = 1
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_LINE_ANALOG = 5
    const val TYPE_LINE_DIGITAL = 6
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_HDMI = 9
    const val TYPE_HDMI_ARC = 10
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_ACCESSORY = 12
    const val TYPE_DOCK = 13
    const val TYPE_AUX_LINE = 19
    const val TYPE_IP = 20
    const val TYPE_BUS = 21
    const val TYPE_USB_HEADSET = 22
    const val TYPE_BUILTIN_SPEAKER_SAFE = 24
    const val TYPE_HDMI_EARC = 29

    private val CAR_HEAD_UNIT_HINTS = listOf(
        "intellilink", "intelli-link", "intelli link",
        "vauxhall", "opel", "myopel",
        "ford sync", "sync 3", "sync 4", "applink",
        "entune", "mylink", "uconnect",
        "android auto", "androidauto", "carplay", "car play",
        "infotainment", "head unit", "headunit",
        "mib2", "mib3", "mib ii",
        "idrive", "mbux", "comand", "mmi ",
        "nissanconnect", "starlink", "sensus",
        "r-link", "rlink", "media-nav", "medianav"
    )

    fun isCarHeadUnitName(name: String): Boolean {
        val n = name.lowercase().trim()
        if (n.isEmpty()) return false
        if (n.contains("intellilink") || n.contains("intelli-link")) return true
        if (Regex("""\bcar\b""").containsMatchIn(n)) return true
        return CAR_HEAD_UNIT_HINTS.any { n.contains(it) }
    }

    fun isUsbAudioType(type: Int): Boolean = when (type) {
        TYPE_USB_DEVICE, TYPE_USB_HEADSET, TYPE_USB_ACCESSORY -> true
        else -> false
    }

    fun isAndroidAutoOrExternalDacType(type: Int): Boolean = when (type) {
        TYPE_BUS, TYPE_DOCK, TYPE_HDMI, TYPE_HDMI_ARC, TYPE_HDMI_EARC,
        TYPE_LINE_DIGITAL, TYPE_LINE_ANALOG, TYPE_AUX_LINE, TYPE_IP -> true
        else -> false
    }

    fun isWiredType(type: Int): Boolean = when (type) {
        TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES -> true
        else -> false
    }

    fun isBluetoothType(type: Int): Boolean = when (type) {
        TYPE_BLUETOOTH_A2DP, TYPE_BLUETOOTH_SCO -> true
        else -> false
    }

    fun isBuiltinSpeaker(type: Int): Boolean = when (type) {
        TYPE_BUILTIN_SPEAKER, TYPE_BUILTIN_SPEAKER_SAFE, TYPE_BUILTIN_EARPIECE -> true
        else -> false
    }

    fun rank(sink: AudioSink): Int {
        var score = 0
        if (isUsbAudioType(sink.type)) score += 100
        if (isAndroidAutoOrExternalDacType(sink.type)) score += 90
        if (isCarHeadUnitName(sink.name)) score += 80
        if (isWiredType(sink.type)) score += 40
        if (isBluetoothType(sink.type)) score += 20
        if (isBuiltinSpeaker(sink.type)) score -= 100
        return score
    }

    fun classify(
        sinks: List<AudioSink>,
        usbCable: Boolean,
        usbAccessory: Boolean
    ): OutputState {
        val namedCar = sinks.any { isCarHeadUnitName(it.name) }
        val usbAudio = sinks.any { isUsbAudioType(it.type) }
        val aaOrDac = sinks.any { isAndroidAutoOrExternalDacType(it.type) }
        val wired = sinks.any { isWiredType(it.type) }

        val best = sinks.maxByOrNull { rank(it) }

        val carLikely = usbAudio || aaOrDac || namedCar || usbAccessory || usbCable || wired

        val kind = when {
            best != null && isUsbAudioType(best.type) -> OutputKind.USB
            best != null && isAndroidAutoOrExternalDacType(best.type) -> OutputKind.USB
            usbAudio || aaOrDac || usbAccessory || (usbCable && (namedCar || !wired)) -> OutputKind.USB
            best != null && isBluetoothType(best.type) -> OutputKind.BLUETOOTH
            best != null && isWiredType(best.type) -> OutputKind.OTHER
            usbCable || usbAccessory -> OutputKind.USB
            else -> OutputKind.PHONE
        }

        val label = labelFor(best, kind, usbCable, usbAccessory)
        return OutputState(kind, label, carLikely)
    }

    private fun labelFor(
        best: AudioSink?,
        kind: OutputKind,
        usbCable: Boolean,
        usbAccessory: Boolean
    ): String {
        val named = best?.name?.trim().orEmpty()
        if (named.isNotBlank() && best != null && !isBuiltinSpeaker(best.type)) {
            return named
        }
        return when (kind) {
            OutputKind.USB -> "USB / Android Auto"
            OutputKind.BLUETOOTH -> named.ifBlank { "Bluetooth" }
            OutputKind.OTHER -> named.ifBlank { "Wired output" }
            OutputKind.PHONE -> if (usbCable || usbAccessory) "USB / Android Auto" else "Phone speaker"
        }
    }
}
