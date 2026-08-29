package com.usbboost.app

enum class SoundPreset {
    FLAT, PODCAST, ROCK, COUNTRY, CUSTOM;

    companion object {
        fun fromKey(key: String): SoundPreset =
            entries.find { it.name == key } ?: FLAT
    }
}

data class EqBands(
    val bass: Int = 0,
    val lowMid: Int = 0,
    val mid: Int = 0,
    val presence: Int = 0,
    val treble: Int = 0
) {
    fun coerced(): EqBands = copy(
        bass = bass.coerceIn(MIN, MAX),
        lowMid = lowMid.coerceIn(MIN, MAX),
        mid = mid.coerceIn(MIN, MAX),
        presence = presence.coerceIn(MIN, MAX),
        treble = treble.coerceIn(MIN, MAX)
    )

    fun isFlat(): Boolean =
        bass == 0 && lowMid == 0 && mid == 0 && presence == 0 && treble == 0

    companion object {
        const val MIN = -12
        const val MAX = 12
    }
}

object EqShapes {
    fun forPreset(preset: SoundPreset): EqBands = when (preset) {
        SoundPreset.FLAT, SoundPreset.CUSTOM -> EqBands()
        SoundPreset.PODCAST -> EqBands(bass = -4, lowMid = 2, mid = 6, presence = 4, treble = 1)
        SoundPreset.ROCK -> EqBands(bass = 5, lowMid = -1, mid = 1, presence = 5, treble = 4)
        SoundPreset.COUNTRY -> EqBands(bass = 3, lowMid = 1, mid = 3, presence = 4, treble = 2)
    }

    fun dbFor(centerHz: Float, eq: EqBands): Int = when {
        centerHz < 150f -> eq.bass
        centerHz < 400f -> eq.lowMid
        centerHz < 1200f -> eq.mid
        centerHz < 4000f -> eq.presence
        else -> eq.treble
    }
}
