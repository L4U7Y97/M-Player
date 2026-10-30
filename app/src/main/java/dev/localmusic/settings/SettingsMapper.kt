package dev.localmusic.settings

import dev.localmusic.domain.SortField

/**
 * Pure conversions between settings values and their stored (DataStore) form (spec §7). No android.* imports.
 */
object SettingsMapper {

    // ---- enums <-> stored strings -------------------------------------------------------------------

    /** Stored form of any enum: its [Enum.name]. */
    fun enumToString(value: Enum<*>): String = value.name

    /**
     * Parses [stored] as an enum constant name of [E]: trimmed and case-insensitive.
     * null / blank / unknown -> [default].
     */
    inline fun <reified E : Enum<E>> enumFromString(stored: String?, default: E): E {
        val key = stored?.trim().orEmpty()
        if (key.isEmpty()) return default
        return enumValues<E>().firstOrNull { it.name.equals(key, ignoreCase = true) } ?: default
    }

    fun themeModeFromString(stored: String?): ThemeMode = enumFromString(stored, ThemeMode.SYSTEM)

    fun accentFromString(stored: String?): Accent = enumFromString(stored, Accent.PURPLE)

    fun sortFieldFromString(stored: String?): SortField = enumFromString(stored, SortField.TRACK_NUMBER)

    // ---- EQ band levels <-> CSV ---------------------------------------------------------------------

    /** `[-300, 0, 150]` -> `"-300,0,150"`; empty list -> `""`. */
    fun bandLevelsToCsv(levels: List<Int>): String = levels.joinToString(",")

    /**
     * `"-300,x,99999"` with range [-1500, 1500] -> `[-300, 0, 1500]`.
     * Each comma-separated entry is trimmed; non-integer entries -> 0; every value clamped to [minMb, maxMb]
     * (bounds are swapped if given reversed). null / blank csv -> empty list.
     * When [bandCount] is non-null the result is truncated or padded with 0 (clamped) to exactly that size.
     */
    fun bandLevelsFromCsv(csv: String?, minMb: Int, maxMb: Int, bandCount: Int? = null): List<Int> {
        val parsed = if (csv.isNullOrBlank()) {
            emptyList()
        } else {
            csv.split(',').map { clampBandLevel(it.trim().toIntOrNull() ?: 0, minMb, maxMb) }
        }
        if (bandCount == null) return parsed
        val n = bandCount.coerceAtLeast(0)
        val pad = clampBandLevel(0, minMb, maxMb)
        return if (parsed.size >= n) parsed.take(n) else parsed + List(n - parsed.size) { pad }
    }

    /** Clamps [levelMb] into [minMb, maxMb] (bounds swapped if reversed). */
    fun clampBandLevel(levelMb: Int, minMb: Int, maxMb: Int): Int {
        val lo = minOf(minMb, maxMb)
        val hi = maxOf(minMb, maxMb)
        return levelMb.coerceIn(lo, hi)
    }

    /** Bass boost strength clamped to 0..1000. */
    fun clampStrength(strength: Int): Int =
        strength.coerceIn(EqSettings.MIN_STRENGTH, EqSettings.MAX_STRENGTH)

    /** Stored preset index: anything below -1 becomes -1 (custom). */
    fun sanitizePresetIndex(index: Int): Int = if (index < EqSettings.CUSTOM_PRESET) EqSettings.CUSTOM_PRESET else index
}
