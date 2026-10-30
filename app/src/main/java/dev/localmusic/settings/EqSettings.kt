package dev.localmusic.settings

/**
 * Equalizer settings (spec §7). Pure Kotlin.
 * [presetIndex] = -1 means custom band levels. [bandLevelsMb] empty = flat (band count unknown until the
 * device Equalizer is created). [bassBoostStrength] is 0..1000.
 */
data class EqSettings(
    val enabled: Boolean = false,
    val presetIndex: Int = CUSTOM_PRESET,
    val bandLevelsMb: List<Int> = emptyList(),
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
) {
    companion object {
        const val CUSTOM_PRESET = -1
        const val MIN_STRENGTH = 0
        const val MAX_STRENGTH = 1000
    }
}
