package dev.localmusic.ui.theme

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure (JVM-testable) color math for the color themes (BUG-3, spec §7). Colors are packed ARGB ints.
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

    /** HSL lightness (0..1) of [argb]. */
    fun lightness(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (max(r, max(g, b)) + min(r, min(g, b))) / 510f
    }

    /** Opaque per-channel sRGB blend from [a] ([t] = 0) to [b] ([t] = 1). */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * [color] when its contrast with [against] is at least [min]; otherwise the color of the same hue whose HSL lightness
     * is closest to [color]'s and reaches [min], moving away from [against] first. The HSL saturation is kept unless that
     * would make the color more colorful (higher chroma) than [color]: darkening a pastel at full saturation would turn
     * a dusty rose into brick red. Luminance is monotonic in lightness along that path, so each direction is a binary
     * search. Black or white (whichever contrasts more) when neither direction can reach [min].
     */
    fun ensureContrast(color: Int, against: Int, min: Double): Int {
        if (contrast(color, against) >= min) return color
        val (h, s) = hueSat(color)
        val start = lightness(color)
        val chroma = (1f - abs(2f * start - 1f)) * s
        fun shade(light: Float): Int {
            val room = 1f - abs(2f * light - 1f)
            return hsl(h, if (room > 0f) minOf(s, chroma / room) else 0f, light)
        }
        val darkerFirst = luminance(against) >= luminance(color)
        for (darker in listOf(darkerFirst, !darkerFirst)) {
            val end = if (darker) 0f else 1f
            if (contrast(shade(end), against) < min) continue
            // [ok] always reaches min and [bad] never does; the passing lightnesses are one interval ending at [end].
            var ok = end
            var bad = start
            repeat(24) {
                val mid = (ok + bad) / 2f
                if (contrast(shade(mid), against) >= min) ok = mid else bad = mid
            }
            return shade(ok)
        }
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        return if (contrast(black, against) >= contrast(white, against)) black else white
    }
}
