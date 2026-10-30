package dev.localmusic.settings

import dev.localmusic.domain.SortSpec

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent seeds (spec §7). QA labels: "Accent Purple" etc. */
enum class Accent(val label: String) {
    PURPLE("Purple"),
    BLUE("Blue"),
    GREEN("Green"),
    ORANGE("Orange"),
    PINK("Pink"),
}

/** All persisted user settings (spec §7). Pure Kotlin. Defaults match the spec. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val accent: Accent = Accent.PURPLE,
    val dynamicColor: Boolean = true,
    val skipSilence: Boolean = false,
    val sort: SortSpec = SortSpec.DEFAULT,
    val eq: EqSettings = EqSettings(),
)
