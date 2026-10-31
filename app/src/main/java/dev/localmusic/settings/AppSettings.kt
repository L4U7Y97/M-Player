package dev.localmusic.settings

import dev.localmusic.domain.SortSpec

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Color themes (spec §7). QA labels: "Theme Purple" etc. The first five are Material tonal schemes built from one seed
 * (the original accents; their stored names are unchanged), the rest are hand-picked palettes. Every theme has a light
 * and a dark variant.
 */
enum class ColorTheme(val label: String) {
    PURPLE("Purple"),
    BLUE("Blue"),
    GREEN("Green"),
    ORANGE("Orange"),
    PINK("Pink"),
    MONOCHROME("Monochrome"),
    CATPPUCCIN("Catppuccin"),
    DRACULA("Dracula"),
    EVERFOREST("Everforest"),
    GRUVBOX("Gruvbox"),
    NORD("Nord"),
    ROSE_PINE("Rosé Pine"),
    SOLARIZED("Solarized"),
    TOKYO_NIGHT("Tokyo Night"),
}

/** All persisted user settings (spec §7). Pure Kotlin. Defaults match the spec. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.PURPLE,
    val dynamicColor: Boolean = true,
    val skipSilence: Boolean = false,
    val sort: SortSpec = SortSpec.DEFAULT,
    val eq: EqSettings = EqSettings(),
)
