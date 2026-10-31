package dev.localmusic.ui.theme

import dev.localmusic.settings.ColorTheme

/**
 * One variant (light or dark) of a hand-picked color theme: the few colors it defines, packed ARGB.
 * [ColorRoles.fromPalette] derives every Material role from them. [subtext] null = derived from text and background.
 */
data class PaletteVariant(
    val background: Int,
    val text: Int,
    val subtext: Int?,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val error: Int,
)

/** A hand-picked color theme: its light and dark variants. */
data class ThemePalette(val light: PaletteVariant, val dark: PaletteVariant) {
    fun variant(dark: Boolean): PaletteVariant = if (dark) this.dark else light
}

/**
 * The hand-picked palettes (spec §7). Colors come from each scheme's published palette (secondary is a neighbor of
 * primary, tertiary a contrasting accent); Monochrome is our own. Pure Kotlin.
 */
object ThemePalettes {

    /** Palette of [theme], or null for the Material themes (Purple … Pink), which are built from one seed. */
    fun of(theme: ColorTheme): ThemePalette? = when (theme) {
        ColorTheme.PURPLE, ColorTheme.BLUE, ColorTheme.GREEN, ColorTheme.ORANGE, ColorTheme.PINK -> null
        ColorTheme.MONOCHROME -> ThemePalette(
            light = variant(
                background = 0xFFFAFAFA, text = 0xFF1A1A1A, subtext = 0xFF5E5E5E,
                primary = 0xFF262626, secondary = 0xFF4D4D4D, tertiary = 0xFF6E6E6E, error = 0xFFB3261E,
            ),
            dark = variant(
                background = 0xFF121212, text = 0xFFEDEDED, subtext = 0xFFA3A3A3,
                primary = 0xFFE3E3E3, secondary = 0xFFBDBDBD, tertiary = 0xFF9E9E9E, error = 0xFFF2B8B5,
            ),
        )
        // Latte / Mocha: base, text, subtext0, mauve, lavender, peach, red.
        ColorTheme.CATPPUCCIN -> ThemePalette(
            light = variant(
                background = 0xFFEFF1F5, text = 0xFF4C4F69, subtext = 0xFF6C6F85,
                primary = 0xFF8839EF, secondary = 0xFF7287FD, tertiary = 0xFFFE640B, error = 0xFFD20F39,
            ),
            dark = variant(
                background = 0xFF1E1E2E, text = 0xFFCDD6F4, subtext = 0xFFA6ADC8,
                primary = 0xFFCBA6F7, secondary = 0xFFB4BEFE, tertiary = 0xFFFAB387, error = 0xFFF38BA8,
            ),
        )
        // Alucard / Dracula: background, foreground, comment, purple, pink, cyan, red.
        ColorTheme.DRACULA -> ThemePalette(
            light = variant(
                background = 0xFFFFFBEB, text = 0xFF1F1F1F, subtext = 0xFF6C664B,
                primary = 0xFF644AC9, secondary = 0xFFA3144D, tertiary = 0xFF036A96, error = 0xFFCB3A2A,
            ),
            dark = variant(
                background = 0xFF282A36, text = 0xFFF8F8F2, subtext = 0xFF6272A4,
                primary = 0xFFBD93F9, secondary = 0xFFFF79C6, tertiary = 0xFF8BE9FD, error = 0xFFFF5555,
            ),
        )
        // Light / dark medium: bg0, fg, grey2, green, aqua, orange, red.
        ColorTheme.EVERFOREST -> ThemePalette(
            light = variant(
                background = 0xFFFDF6E3, text = 0xFF5C6A72, subtext = 0xFF829181,
                primary = 0xFF8DA101, secondary = 0xFF35A77C, tertiary = 0xFFF57D26, error = 0xFFF85552,
            ),
            dark = variant(
                background = 0xFF2D353B, text = 0xFFD3C6AA, subtext = 0xFF9DA9A0,
                primary = 0xFFA7C080, secondary = 0xFF83C092, tertiary = 0xFFE69875, error = 0xFFE67E80,
            ),
        )
        // Light (faded) / dark (bright): bg0, fg1, fg4, yellow, orange, aqua, red.
        ColorTheme.GRUVBOX -> ThemePalette(
            light = variant(
                background = 0xFFFBF1C7, text = 0xFF3C3836, subtext = 0xFF7C6F64,
                primary = 0xFFB57614, secondary = 0xFFAF3A03, tertiary = 0xFF427B58, error = 0xFF9D0006,
            ),
            dark = variant(
                background = 0xFF282828, text = 0xFFEBDBB2, subtext = 0xFFA89984,
                primary = 0xFFFABD2F, secondary = 0xFFFE8019, tertiary = 0xFF8EC07C, error = 0xFFFB4934,
            ),
        )
        // Snow Storm / Polar Night: background nord6 / nord0, text nord0 / nord6, subtext nord3 (light), Frost nord10 /
        // nord8 and nord9, Aurora nord15 and nord11.
        ColorTheme.NORD -> ThemePalette(
            light = variant(
                background = 0xFFECEFF4, text = 0xFF2E3440, subtext = 0xFF4C566A,
                primary = 0xFF5E81AC, secondary = 0xFF81A1C1, tertiary = 0xFFB48EAD, error = 0xFFBF616A,
            ),
            dark = variant(
                background = 0xFF2E3440, text = 0xFFECEFF4, subtext = null,
                primary = 0xFF88C0D0, secondary = 0xFF81A1C1, tertiary = 0xFFB48EAD, error = 0xFFBF616A,
            ),
        )
        // Dawn / main: base, text, subtle, rose, iris, pine (dawn) or foam (main), love.
        ColorTheme.ROSE_PINE -> ThemePalette(
            light = variant(
                background = 0xFFFAF4ED, text = 0xFF575279, subtext = 0xFF797593,
                primary = 0xFFD7827E, secondary = 0xFF907AA9, tertiary = 0xFF286983, error = 0xFFB4637A,
            ),
            dark = variant(
                background = 0xFF191724, text = 0xFFE0DEF4, subtext = 0xFF908CAA,
                primary = 0xFFEBBCBA, secondary = 0xFFC4A7E7, tertiary = 0xFF9CCFD8, error = 0xFFEB6F92,
            ),
        )
        // base3 / base03 backgrounds, base01 / base1 text, base00 / base0 subtext; blue, cyan, yellow, red.
        ColorTheme.SOLARIZED -> ThemePalette(
            light = variant(
                background = 0xFFFDF6E3, text = 0xFF586E75, subtext = 0xFF657B83,
                primary = 0xFF268BD2, secondary = 0xFF2AA198, tertiary = 0xFFB58900, error = 0xFFDC322F,
            ),
            dark = variant(
                background = 0xFF002B36, text = 0xFF93A1A1, subtext = 0xFF839496,
                primary = 0xFF268BD2, secondary = 0xFF2AA198, tertiary = 0xFFB58900, error = 0xFFDC322F,
            ),
        )
        // Day / night: bg, fg, fg_dark (day) or dark5 (night), blue, magenta, orange, red.
        ColorTheme.TOKYO_NIGHT -> ThemePalette(
            light = variant(
                background = 0xFFE1E2E7, text = 0xFF3760BF, subtext = 0xFF6172B0,
                primary = 0xFF2E7DE9, secondary = 0xFF9854F1, tertiary = 0xFFB15C00, error = 0xFFF52A65,
            ),
            dark = variant(
                background = 0xFF1A1B26, text = 0xFFC0CAF5, subtext = 0xFF737AA2,
                primary = 0xFF7AA2F7, secondary = 0xFFBB9AF7, tertiary = 0xFFFF9E64, error = 0xFFF7768E,
            ),
        )
    }

    private fun variant(
        background: Long,
        text: Long,
        subtext: Long?,
        primary: Long,
        secondary: Long,
        tertiary: Long,
        error: Long,
    ) = PaletteVariant(
        background = background.toInt(),
        text = text.toInt(),
        subtext = subtext?.toInt(),
        primary = primary.toInt(),
        secondary = secondary.toInt(),
        tertiary = tertiary.toInt(),
        error = error.toInt(),
    )
}
