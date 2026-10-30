package dev.localmusic.ui.theme

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure (JVM-testable) color math for the accent schemes (BUG-3). Colors are packed ARGB ints.
 *
 * A "tone" is Material's perceptual lightness (CIE L*, 0 = black, 100 = white). [tone] finds the color with the
 * given hue and HSL saturation whose L* matches the requested tone, so generated roles get Material-like contrast
 * (e.g. tone 40 vs tone 100 for primary / onPrimary) without pulling in material-color-utilities.
 */
object ToneMath {

    /** Hue (degrees 0..360) and HSL saturation (0..1) of [argb]. */
    fun hueSat(argb: Int): Pair<Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val l = (mx + mn) / 2f
        if (d == 0f) return 0f to 0f
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return ((h + 360f) % 360f) to s.coerceIn(0f, 1f)
    }

    /** Opaque ARGB for HSL ([hue] degrees, [sat] 0..1, [light] 0..1). */
    fun hsl(hue: Float, sat: Float, light: Float): Int {
        val c = (1f - abs(2f * light - 1f)) * sat
        val hp = ((hue % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = light - c / 2f
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }

    private fun linear(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance Y (0..1) of [argb]. */
    fun luminance(argb: Int): Double =
        0.2126 * linear((argb shr 16) and 0xFF) + 0.7152 * linear((argb shr 8) and 0xFF) + 0.0722 * linear(argb and 0xFF)

    /** CIE L* (0..100) of [argb]. */
    fun lstar(argb: Int): Double {
        val y = luminance(argb)
        val f = if (y > 216.0 / 24389.0) cbrt(y) else (24389.0 / 27.0 * y + 16.0) / 116.0
        return 116.0 * f - 16.0
    }

    /** WCAG contrast ratio between two opaque colors. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** The color with [hue] and [sat] whose L* is closest to [tone] (0..100). Tone 0 / 100 -> black / white. */
    fun tone(hue: Float, sat: Float, tone: Int): Int {
        if (tone <= 0) return 0xFF000000.toInt()
        if (tone >= 100) return 0xFFFFFFFF.toInt()
        var lo = 0f
        var hi = 1f
        repeat(24) {
            val mid = (lo + hi) / 2f
            if (lstar(hsl(hue, sat, mid)) < tone) lo = mid else hi = mid
        }
        return hsl(hue, sat, (lo + hi) / 2f)
    }
}

/** Every accent-dependent Material 3 color role, as ARGB ints (BUG-3). */
data class AccentRoles(
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
) {
    companion object {
        /**
         * Material 3 tone mapping from one [seed] color: primary palette = seed hue/saturation, secondary = same hue at
         * a third of the saturation, tertiary = hue + 60°, neutrals = seed hue at very low saturation (so surfaces and
         * outlines are tinted by the accent, not by the default purple). Light uses tones 40/100/90/10, dark uses
         * 80/20/30/90 for each accent group (Material's baseline).
         */
        fun fromSeed(seed: Int, dark: Boolean): AccentRoles {
            val (h, s) = ToneMath.hueSat(seed)
            fun p(t: Int) = ToneMath.tone(h, s, t)
            fun s2(t: Int) = ToneMath.tone(h, s * 0.35f, t)
            fun t3(t: Int) = ToneMath.tone(h + 60f, s * 0.6f, t)
            fun n(t: Int) = ToneMath.tone(h, 0.06f, t)
            fun nv(t: Int) = ToneMath.tone(h, 0.12f, t)
            return if (dark) {
                AccentRoles(
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
                AccentRoles(
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
    }
}
