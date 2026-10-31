package dev.localmusic.ui.theme

/**
 * Every theme-dependent Material 3 color role, as ARGB ints (BUG-3, spec §7); the unused *Fixed roles are left out.
 * Built from one seed ([fromSeed], the Material themes) or from a hand-picked palette ([fromPalette]). The error roles
 * are null for seed themes, which keep Material's baseline error colors.
 */
data class ColorRoles(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val outline: Int,
    val outlineVariant: Int,
    val surfaceDim: Int,
    val surfaceBright: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val error: Int? = null,
    val onError: Int? = null,
    val errorContainer: Int? = null,
    val onErrorContainer: Int? = null,
) {
    companion object {
        /**
         * Material 3 tone mapping from one [seed] color: primary palette = seed hue/saturation, secondary = same hue at
         * a third of the saturation, tertiary = hue + 60°, neutrals = seed hue at very low saturation (so surfaces and
         * outlines are tinted by the accent, not by the default purple). Light uses tones 40/100/90/10, dark uses
         * 80/20/30/90 for each accent group (Material's baseline).
         */
        fun fromSeed(seed: Int, dark: Boolean): ColorRoles {
            val (h, s) = ToneMath.hueSat(seed)
            fun p(t: Int) = ToneMath.tone(h, s, t)
            fun s2(t: Int) = ToneMath.tone(h, s * 0.35f, t)
            fun t3(t: Int) = ToneMath.tone(h + 60f, s * 0.6f, t)
            fun n(t: Int) = ToneMath.tone(h, 0.06f, t)
            fun nv(t: Int) = ToneMath.tone(h, 0.12f, t)
            return if (dark) {
                ColorRoles(
                    primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
                    inversePrimary = p(40),
                    secondary = s2(80), onSecondary = s2(20), secondaryContainer = s2(30), onSecondaryContainer = s2(90),
                    tertiary = t3(80), onTertiary = t3(20), tertiaryContainer = t3(30), onTertiaryContainer = t3(90),
                    background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
                    surfaceVariant = nv(30), onSurfaceVariant = nv(80),
                    inverseSurface = n(90), inverseOnSurface = n(20),
                    outline = nv(60), outlineVariant = nv(30),
                    surfaceDim = n(6), surfaceBright = n(24),
                    surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
                    surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
                )
            } else {
                ColorRoles(
                    primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
                    inversePrimary = p(80),
                    secondary = s2(40), onSecondary = s2(100), secondaryContainer = s2(90), onSecondaryContainer = s2(10),
                    tertiary = t3(40), onTertiary = t3(100), tertiaryContainer = t3(90), onTertiaryContainer = t3(10),
                    background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
                    surfaceVariant = nv(90), onSurfaceVariant = nv(30),
                    inverseSurface = n(20), inverseOnSurface = n(95),
                    outline = nv(50), outlineVariant = nv(80),
                    surfaceDim = n(87), surfaceBright = n(98),
                    surfaceContainerLowest = n(100), surfaceContainerLow = n(96), surfaceContainer = n(94),
                    surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
                )
            }
        }

        /**
         * Every role from one variant of a hand-picked palette. The palette's colors are kept where they are legible;
         * where they are not, only their lightness is nudged (hue kept, never more colorful than the palette color).
         * The floors: text >= 7:1 on the background. Accents and error >= 4.5:1 on surfaceContainerHigh, the highest
         * surface they're drawn on as text (dialog buttons), and so on the background and the lower containers too.
         * Secondary text >= 4.5:1 and outline >= 3:1 (the thumb of a switch that's off) on surfaceContainerHighest. The
         * background is the on-color of every accent, which the accent floor makes legible. Surfaces step from the
         * background toward the text color (lighter in dark themes, darker in light ones, by Material's tone steps);
         * containers mix the accent into the background.
         */
        fun fromPalette(p: PaletteVariant, dark: Boolean): ColorRoles {
            val bg = p.background
            val text = ToneMath.ensureContrast(p.text, bg, 7.0)
            fun toward(t: Float) = ToneMath.mix(bg, text, t)
            val high = toward(if (dark) 0.11f else 0.065f)
            val highest = toward(if (dark) 0.15f else 0.09f)
            fun accent(c: Int) = ToneMath.ensureContrast(c, high, 4.5)
            fun container(c: Int) = ToneMath.mix(bg, c, if (dark) 0.3f else 0.16f)
            fun onContainer(c: Int, container: Int) =
                ToneMath.ensureContrast(ToneMath.mix(c, text, if (dark) 0.4f else 0.5f), container, 4.5)

            val subtext = ToneMath.ensureContrast(p.subtext ?: ToneMath.mix(text, bg, 0.3f), highest, 4.5)
            val primary = accent(p.primary)
            val secondary = accent(p.secondary)
            val tertiary = accent(p.tertiary)
            val error = accent(p.error)
            val primaryContainer = container(primary)
            val secondaryContainer = container(secondary)
            val tertiaryContainer = container(tertiary)
            val errorContainer = container(error)
            return ColorRoles(
                primary = primary, onPrimary = bg,
                primaryContainer = primaryContainer, onPrimaryContainer = onContainer(primary, primaryContainer),
                inversePrimary = ToneMath.ensureContrast(primary, text, 4.5),
                secondary = secondary, onSecondary = bg,
                secondaryContainer = secondaryContainer, onSecondaryContainer = onContainer(secondary, secondaryContainer),
                tertiary = tertiary, onTertiary = bg,
                tertiaryContainer = tertiaryContainer, onTertiaryContainer = onContainer(tertiary, tertiaryContainer),
                background = bg, onBackground = text, surface = bg, onSurface = text,
                surfaceVariant = toward(if (dark) 0.2f else 0.1f), onSurfaceVariant = subtext,
                inverseSurface = text, inverseOnSurface = bg,
                outline = ToneMath.ensureContrast(toward(if (dark) 0.55f else 0.5f), highest, 3.0),
                outlineVariant = toward(if (dark) 0.25f else 0.2f),
                surfaceDim = if (dark) bg else toward(0.12f),
                surfaceBright = if (dark) toward(0.18f) else bg,
                surfaceContainerLowest = if (dark) ToneMath.mix(bg, BLACK, 0.3f) else ToneMath.mix(bg, WHITE, 0.6f),
                surfaceContainerLow = toward(if (dark) 0.04f else 0.025f),
                surfaceContainer = toward(if (dark) 0.07f else 0.045f),
                surfaceContainerHigh = high,
                surfaceContainerHighest = highest,
                error = error, onError = bg,
                errorContainer = errorContainer, onErrorContainer = onContainer(error, errorContainer),
            )
        }

        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
