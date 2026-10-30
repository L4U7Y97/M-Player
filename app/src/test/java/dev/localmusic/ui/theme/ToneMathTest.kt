package dev.localmusic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class ToneMathTest {

    private val green = 0xFF2E6B30.toInt()
    private val seeds = listOf(green, 0xFF1F5FA8.toInt(), 0xFF9A4600.toInt(), 0xFFA0306A.toInt())

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    @Test
    fun tone_hitsRequestedLstar() {
        val (h, s) = ToneMath.hueSat(green)
        for (t in listOf(10, 20, 30, 40, 50, 80, 90, 95)) {
            val c = ToneMath.tone(h, s, t)
            assertEquals("tone $t", t.toDouble(), ToneMath.lstar(c), 1.5)
        }
        assertEquals(0xFFFFFFFF.toInt(), ToneMath.tone(h, s, 100))
        assertEquals(0xFF000000.toInt(), ToneMath.tone(h, s, 0))
    }

    @Test
    fun hueSat_roundTripsThroughHsl() {
        val (h, s) = ToneMath.hueSat(0xFF00FF00.toInt())
        assertEquals(120f, h, 0.5f)
        assertEquals(1f, s, 0.01f)
        assertEquals(0xFF00FF00.toInt(), ToneMath.hsl(120f, 1f, 0.5f))
    }

    @Test
    fun accentRoles_onColorsContrastWithTheirBackgrounds() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val r = AccentRoles.fromSeed(seed, dark)
            val label = "seed=${Integer.toHexString(seed)} dark=$dark"
            assertTrue("$label onPrimary", ToneMath.contrast(r.primary, r.onPrimary) >= 4.5)
            assertTrue("$label onPrimaryContainer", ToneMath.contrast(r.primaryContainer, r.onPrimaryContainer) >= 4.5)
            assertTrue("$label onSecondaryContainer", ToneMath.contrast(r.secondaryContainer, r.onSecondaryContainer) >= 4.5)
            assertTrue("$label onSurface", ToneMath.contrast(r.surface, r.onSurface) >= 7.0)
        }
    }

    @Test
    fun accentRoles_followSeedHue_notDefaultPurple() {
        // BUG-3: Green's dark onPrimary was Material's purple 0xFF381E72. Every accent role must carry the seed hue.
        for (seed in seeds) {
            val seedHue = ToneMath.hueSat(seed).first
            val r = AccentRoles.fromSeed(seed, dark = true)
            for (role in listOf(r.primary, r.onPrimary, r.primaryContainer, r.onPrimaryContainer, r.secondaryContainer)) {
                val hue = ToneMath.hueSat(role).first
                assertTrue("seed ${Integer.toHexString(seed)} role ${Integer.toHexString(role)}", hueDistance(seedHue, hue) < 15f)
            }
            assertTrue(r.onPrimary != 0xFF381E72.toInt())
        }
    }
}
