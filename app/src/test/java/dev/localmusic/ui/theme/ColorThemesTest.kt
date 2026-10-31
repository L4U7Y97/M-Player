package dev.localmusic.ui.theme

import dev.localmusic.settings.ColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class ColorThemesTest {

    private val modes = listOf(false, true)
    private val paletteThemes = ColorTheme.values().filter { ThemePalettes.of(it) != null }

    private fun palette(theme: ColorTheme, dark: Boolean) = ThemePalettes.of(theme)!!.variant(dark)

    private fun roles(theme: ColorTheme, dark: Boolean) = ColorRoles.fromPalette(palette(theme, dark), dark)

    private fun assertContrast(label: String, a: Int, b: Int, min: Double) {
        val c = ToneMath.contrast(a, b)
        assertTrue("$label: %.2f < $min".format(c), c >= min)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    /** HSL chroma in channel steps: largest minus smallest channel (0 = gray). */
    private fun chroma(argb: Int): Int {
        val channels = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
        return channels.max() - channels.min()
    }

    @Test
    fun materialThemesUseASeed_everyOtherThemeHasAPalette() {
        val seeded = listOf(ColorTheme.PURPLE, ColorTheme.BLUE, ColorTheme.GREEN, ColorTheme.ORANGE, ColorTheme.PINK)
        for (theme in ColorTheme.values()) {
            if (theme in seeded) assertNull("$theme", ThemePalettes.of(theme)) else assertNotNull("$theme", ThemePalettes.of(theme))
        }
        assertEquals(9, paletteThemes.size)
    }

    @Test
    fun paletteRoles_meetContrastMinimums() {
        for (theme in paletteThemes) for (dark in modes) {
            val r = roles(theme, dark)
            val t = "$theme dark=$dark"
            // Every surface the app draws text on: the background, dialogs and menus, the switch track (highest).
            val surfaces = listOf(
                "surface" to r.surface,
                "surfaceContainerLowest" to r.surfaceContainerLowest,
                "surfaceContainerLow" to r.surfaceContainerLow,
                "surfaceContainer" to r.surfaceContainer,
                "surfaceContainerHigh" to r.surfaceContainerHigh,
                "surfaceContainerHighest" to r.surfaceContainerHighest,
            )
            assertContrast("$t onSurface/surface", r.onSurface, r.surface, 7.0)
            for ((name, surface) in surfaces) {
                assertContrast("$t onSurface/$name", r.onSurface, surface, 4.5)
                assertContrast("$t onSurfaceVariant/$name", r.onSurfaceVariant, surface, 4.5)
                assertContrast("$t outline/$name", r.outline, surface, 3.0)
            }
            for ((name, color) in listOf("primary" to r.primary, "secondary" to r.secondary, "tertiary" to r.tertiary, "error" to r.error!!)) {
                // Text up to surfaceContainerHigh (dialog buttons); only indicators sit on surfaceContainerHighest.
                for ((surfaceName, surface) in surfaces.dropLast(1)) assertContrast("$t $name/$surfaceName", color, surface, 4.5)
                assertContrast("$t $name/surfaceContainerHighest", color, r.surfaceContainerHighest, 3.0)
            }
            assertContrast("$t onPrimary", r.onPrimary, r.primary, 4.5)
            assertContrast("$t onSecondary", r.onSecondary, r.secondary, 4.5)
            assertContrast("$t onTertiary", r.onTertiary, r.tertiary, 4.5)
            assertContrast("$t onError", r.onError!!, r.error!!, 4.5)
            assertContrast("$t onPrimaryContainer", r.onPrimaryContainer, r.primaryContainer, 4.5)
            assertContrast("$t onSecondaryContainer", r.onSecondaryContainer, r.secondaryContainer, 4.5)
            assertContrast("$t onTertiaryContainer", r.onTertiaryContainer, r.tertiaryContainer, 4.5)
            assertContrast("$t onErrorContainer", r.onErrorContainer!!, r.errorContainer!!, 4.5)
            assertContrast("$t inverseOnSurface", r.inverseOnSurface, r.inverseSurface, 4.5)
            assertContrast("$t inversePrimary", r.inversePrimary, r.inverseSurface, 4.5)
        }
    }

    @Test
    fun paletteRoles_keepThePaletteColors() {
        for (theme in paletteThemes) for (dark in modes) {
            val p = palette(theme, dark)
            val r = roles(theme, dark)
            val t = "$theme dark=$dark"
            assertEquals("$t background", p.background, r.background)
            assertEquals("$t surface", p.background, r.surface)
            val accents = listOf(
                Triple("primary", p.primary, r.primary), Triple("secondary", p.secondary, r.secondary),
                Triple("tertiary", p.tertiary, r.tertiary), Triple("error", p.error, r.error!!),
            )
            for ((name, original, derived) in accents) {
                // Colors legible where accents are drawn are used as they are; the rest only change lightness, so the
                // hue stays and they never get more colorful than the palette's color.
                if (ToneMath.contrast(original, r.surfaceContainerHigh) >= 4.5) assertEquals("$t $name", original, derived)
                assertTrue("$t $name chroma ${chroma(original)} -> ${chroma(derived)}", chroma(derived) <= chroma(original) + 1)
                val (hue, sat) = ToneMath.hueSat(original)
                if (sat >= 0.1f) {
                    val derivedHue = ToneMath.hueSat(derived).first
                    assertTrue("$t $name hue $hue -> $derivedHue", hueDistance(hue, derivedHue) < 10f)
                }
            }
        }
    }

    @Test
    fun darkVariantsAreDark_lightVariantsAreLight() {
        for (theme in paletteThemes) {
            val light = roles(theme, dark = false)
            val dark = roles(theme, dark = true)
            assertTrue("$theme light background", ToneMath.lstar(light.background) > 80)
            assertTrue("$theme dark background", ToneMath.lstar(dark.background) < 25)
            assertTrue("$theme light text", ToneMath.lstar(light.onSurface) < ToneMath.lstar(light.surface))
            assertTrue("$theme dark text", ToneMath.lstar(dark.onSurface) > ToneMath.lstar(dark.surface))
        }
    }

    @Test
    fun surfaceContainers_stepAwayFromTheBackground() {
        // Material: containers get lighter with elevation in dark themes and darker in light ones; lowest is the other way.
        for (theme in paletteThemes) for (dark in modes) {
            val r = roles(theme, dark)
            val t = "$theme dark=$dark"
            val steps = listOf(r.surface, r.surfaceContainerLow, r.surfaceContainer, r.surfaceContainerHigh, r.surfaceContainerHighest)
                .map(ToneMath::lstar)
            for (i in 1 until steps.size) {
                assertTrue("$t step $i: ${steps[i - 1]} -> ${steps[i]}", if (dark) steps[i] > steps[i - 1] else steps[i] < steps[i - 1])
            }
            val lowest = ToneMath.lstar(r.surfaceContainerLowest)
            assertTrue("$t lowest", if (dark) lowest < steps[0] else lowest >= steps[0])
        }
    }

    @Test
    fun paletteThemes_lookDistinct() {
        for (dark in modes) {
            val looks = paletteThemes.map { roles(it, dark).let { r -> r.background to r.primary } }
            assertEquals("dark=$dark", looks.size, looks.toSet().size)
        }
    }

    @Test
    fun ensureContrast_keepsLegibleColors_nudgesTheRestAwayFromTheBackground() {
        val cream = 0xFFFDF6E3.toInt()
        val navy = 0xFF002B36.toInt()
        val blue = 0xFF268BD2.toInt() // Solarized blue: below 4.5:1 on both Solarized backgrounds
        assertTrue(ToneMath.contrast(blue, cream) < 4.5)

        val darker = ToneMath.ensureContrast(blue, cream, 4.5)
        assertTrue(ToneMath.contrast(darker, cream) >= 4.5)
        assertTrue("closest legible color, not far past it", ToneMath.contrast(darker, cream) < 4.7)
        assertTrue(ToneMath.lstar(darker) < ToneMath.lstar(blue))
        assertTrue(hueDistance(ToneMath.hueSat(blue).first, ToneMath.hueSat(darker).first) < 3f)

        val lighter = ToneMath.ensureContrast(blue, navy, 4.5)
        assertTrue(ToneMath.contrast(lighter, navy) >= 4.5)
        assertTrue(ToneMath.lstar(lighter) > ToneMath.lstar(blue))

        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals("already legible", blue, ToneMath.ensureContrast(blue, white, 3.0))
        assertEquals(black, ToneMath.ensureContrast(black, white, 20.0))
        val gray = 0xFF777777.toInt()
        assertEquals("unreachable: best of black / white", black, ToneMath.ensureContrast(gray, gray, 30.0))
    }

    @Test
    fun ensureContrast_neverMakesAColorMoreColorful() {
        // Rosé Pine Dawn's rose: darkening it at its HSL saturation would give brick red #C44540, not a darker rose.
        val rose = 0xFFD7827E.toInt()
        val dawn = 0xFFFAF4ED.toInt()
        val darker = ToneMath.ensureContrast(rose, dawn, 4.5)
        assertTrue(ToneMath.contrast(darker, dawn) >= 4.5)
        assertTrue("closest legible color, not far past it", ToneMath.contrast(darker, dawn) < 4.7)
        assertTrue("chroma ${chroma(rose)} -> ${chroma(darker)}", chroma(darker) <= chroma(rose) + 1)
        assertTrue(hueDistance(ToneMath.hueSat(rose).first, ToneMath.hueSat(darker).first) < 3f)

        // Lightening through the middle of the lightness range is where full saturation would add the most chroma.
        val navy = 0xFF002B36.toInt()
        val blue = 0xFF1A5C8C.toInt()
        val lighter = ToneMath.ensureContrast(blue, navy, 4.5)
        assertTrue(ToneMath.contrast(lighter, navy) >= 4.5)
        assertTrue("chroma ${chroma(blue)} -> ${chroma(lighter)}", chroma(lighter) <= chroma(blue) + 1)
    }

    @Test
    fun mix_endpointsAndMidpoint() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(black, ToneMath.mix(black, white, 0f))
        assertEquals(white, ToneMath.mix(black, white, 1f))
        assertEquals(0xFF808080.toInt(), ToneMath.mix(black, white, 0.5f))
        assertEquals(0xFF102030.toInt(), ToneMath.mix(0xFF102030.toInt(), 0xFF102030.toInt(), 0.37f))
        assertEquals(0xFF8C6E4B.toInt(), ToneMath.mix(0xFFC86432.toInt(), 0xFF507864.toInt(), 0.5f))
    }
}
