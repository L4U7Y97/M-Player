package dev.localmusic.settings

import dev.localmusic.domain.SortField
import dev.localmusic.domain.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMapperTest {

    // ---- defaults -------------------------------------------------------------------------------------

    @Test
    fun appSettings_defaultsMatchSpec() {
        val s = AppSettings()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertFalse(s.pureBlack)
        assertEquals(Accent.PURPLE, s.accent)
        assertTrue(s.dynamicColor)
        assertFalse(s.skipSilence)
        assertEquals(SortSpec(SortField.TRACK_NUMBER, true), s.sort)
        assertEquals(EqSettings(), s.eq)
    }

    @Test
    fun eqSettings_defaultsOffCustomFlatBassOff() {
        val eq = EqSettings()
        assertFalse(eq.enabled)
        assertEquals(EqSettings.CUSTOM_PRESET, eq.presetIndex)
        assertEquals(-1, EqSettings.CUSTOM_PRESET)
        assertTrue(eq.bandLevelsMb.isEmpty())
        assertFalse(eq.bassBoostEnabled)
        assertEquals(0, eq.bassBoostStrength)
        assertEquals(0, EqSettings.MIN_STRENGTH)
        assertEquals(1000, EqSettings.MAX_STRENGTH)
    }

    @Test
    fun accent_hasFiveSeedsWithLabels() {
        assertEquals(listOf("Purple", "Blue", "Green", "Orange", "Pink"), Accent.values().map { it.label })
    }

    // ---- enums ----------------------------------------------------------------------------------------

    @Test
    fun enum_roundTripAllValues() {
        for (v in ThemeMode.values()) assertEquals(v, SettingsMapper.themeModeFromString(SettingsMapper.enumToString(v)))
        for (v in Accent.values()) assertEquals(v, SettingsMapper.accentFromString(SettingsMapper.enumToString(v)))
        for (v in SortField.values()) assertEquals(v, SettingsMapper.sortFieldFromString(SettingsMapper.enumToString(v)))
    }

    @Test
    fun enumToString_isName() {
        assertEquals("DARK", SettingsMapper.enumToString(ThemeMode.DARK))
        assertEquals("DATE_ADDED", SettingsMapper.enumToString(SortField.DATE_ADDED))
    }

    @Test
    fun enumFromString_caseInsensitiveAndTrimmed() {
        assertEquals(ThemeMode.DARK, SettingsMapper.themeModeFromString("dark"))
        assertEquals(ThemeMode.LIGHT, SettingsMapper.themeModeFromString("  Light "))
        assertEquals(Accent.GREEN, SettingsMapper.accentFromString("green"))
        assertEquals(SortField.FILE_NAME, SettingsMapper.sortFieldFromString("file_name"))
    }

    @Test
    fun enumFromString_nullBlankUnknown_default() {
        for (bad in listOf(null, "", "   ", "MAGENTA", "1", "PURPLE_X")) {
            assertEquals("stored=$bad", Accent.PURPLE, SettingsMapper.accentFromString(bad))
            assertEquals("stored=$bad", ThemeMode.SYSTEM, SettingsMapper.themeModeFromString(bad))
            assertEquals("stored=$bad", SortField.TRACK_NUMBER, SettingsMapper.sortFieldFromString(bad))
        }
    }

    @Test
    fun enumFromString_genericWithExplicitDefault() {
        assertEquals(ThemeMode.DARK, SettingsMapper.enumFromString("nope", ThemeMode.DARK))
        assertEquals(Accent.PINK, SettingsMapper.enumFromString("PINK", Accent.BLUE))
    }

    // ---- band levels CSV ------------------------------------------------------------------------------

    @Test
    fun bandLevelsToCsv_format() {
        assertEquals("-300,0,150", SettingsMapper.bandLevelsToCsv(listOf(-300, 0, 150)))
        assertEquals("", SettingsMapper.bandLevelsToCsv(emptyList()))
        assertEquals("7", SettingsMapper.bandLevelsToCsv(listOf(7)))
    }

    @Test
    fun bandLevels_roundTrip() {
        for (levels in listOf(listOf(-300, 0, 150), emptyList(), listOf(1500, -1500, 0, 1, -1))) {
            val csv = SettingsMapper.bandLevelsToCsv(levels)
            assertEquals(levels, SettingsMapper.bandLevelsFromCsv(csv, -1500, 1500))
        }
    }

    @Test
    fun bandLevelsFromCsv_invalidEntriesZeroAndClamped() {
        assertEquals(listOf(-300, 0, 1500), SettingsMapper.bandLevelsFromCsv("-300,x,99999", -1500, 1500))
        assertEquals(listOf(-1500), SettingsMapper.bandLevelsFromCsv("-99999", -1500, 1500))
        assertEquals(listOf(0, 0, 0), SettingsMapper.bandLevelsFromCsv("1.5,,abc", -1500, 1500))
    }

    @Test
    fun bandLevelsFromCsv_trimsEntries() {
        assertEquals(listOf(-300, 0, 150), SettingsMapper.bandLevelsFromCsv(" -300 , 0 ,150 ", -1500, 1500))
    }

    @Test
    fun bandLevelsFromCsv_nullOrBlank_empty() {
        assertEquals(emptyList<Int>(), SettingsMapper.bandLevelsFromCsv(null, -1500, 1500))
        assertEquals(emptyList<Int>(), SettingsMapper.bandLevelsFromCsv("", -1500, 1500))
        assertEquals(emptyList<Int>(), SettingsMapper.bandLevelsFromCsv("  ", -1500, 1500))
    }

    @Test
    fun bandLevelsFromCsv_invalidEntryClampedWhenZeroOutOfRange() {
        assertEquals(listOf(100, 200), SettingsMapper.bandLevelsFromCsv("x,500", 100, 200))
    }

    @Test
    fun bandLevelsFromCsv_bandCountTruncatesOrPads() {
        assertEquals(listOf(1, 2), SettingsMapper.bandLevelsFromCsv("1,2,3,4", -1500, 1500, bandCount = 2))
        assertEquals(listOf(1, 0, 0, 0, 0), SettingsMapper.bandLevelsFromCsv("1", -1500, 1500, bandCount = 5))
        assertEquals(listOf(0, 0, 0), SettingsMapper.bandLevelsFromCsv(null, -1500, 1500, bandCount = 3))
        assertEquals(emptyList<Int>(), SettingsMapper.bandLevelsFromCsv("1,2", -1500, 1500, bandCount = 0))
        assertEquals(emptyList<Int>(), SettingsMapper.bandLevelsFromCsv("1,2", -1500, 1500, bandCount = -3))
    }

    @Test
    fun bandLevelsFromCsv_paddingIsClampedZero() {
        assertEquals(listOf(150, 100), SettingsMapper.bandLevelsFromCsv("150", 100, 200, bandCount = 2))
    }

    @Test
    fun clampBandLevel_boundsAndReversedBounds() {
        assertEquals(1500, SettingsMapper.clampBandLevel(2000, -1500, 1500))
        assertEquals(-1500, SettingsMapper.clampBandLevel(-2000, -1500, 1500))
        assertEquals(10, SettingsMapper.clampBandLevel(10, -1500, 1500))
        assertEquals(1500, SettingsMapper.clampBandLevel(2000, 1500, -1500))
        assertEquals(-1500, SettingsMapper.clampBandLevel(-2000, 1500, -1500))
    }

    // ---- strength / preset ----------------------------------------------------------------------------

    @Test
    fun clampStrength_zeroToThousand() {
        assertEquals(0, SettingsMapper.clampStrength(-5))
        assertEquals(0, SettingsMapper.clampStrength(0))
        assertEquals(500, SettingsMapper.clampStrength(500))
        assertEquals(1000, SettingsMapper.clampStrength(1000))
        assertEquals(1000, SettingsMapper.clampStrength(Int.MAX_VALUE))
    }

    @Test
    fun sanitizePresetIndex_belowCustomBecomesCustom() {
        assertEquals(-1, SettingsMapper.sanitizePresetIndex(-1))
        assertEquals(-1, SettingsMapper.sanitizePresetIndex(-7))
        assertEquals(-1, SettingsMapper.sanitizePresetIndex(Int.MIN_VALUE))
        assertEquals(0, SettingsMapper.sanitizePresetIndex(0))
        assertEquals(4, SettingsMapper.sanitizePresetIndex(4))
    }

    // ---- full settings round-trip through the stored (string) form ------------------------------------

    @Test
    fun fullSettings_roundTripThroughStoredForm() {
        val original = AppSettings(
            themeMode = ThemeMode.DARK, pureBlack = true, accent = Accent.ORANGE, dynamicColor = false,
            skipSilence = true, sort = SortSpec(SortField.DATE_ADDED, ascending = false),
            eq = EqSettings(enabled = true, presetIndex = 2, bandLevelsMb = listOf(-300, 0, 150, 600, -1500),
                bassBoostEnabled = true, bassBoostStrength = 750),
        )
        // Mimic what SettingsRepository stores: strings for enums/CSV, primitives for the rest.
        val stored = mapOf(
            "theme" to SettingsMapper.enumToString(original.themeMode),
            "accent" to SettingsMapper.enumToString(original.accent),
            "sortField" to SettingsMapper.enumToString(original.sort.field),
            "bands" to SettingsMapper.bandLevelsToCsv(original.eq.bandLevelsMb),
        )
        val restored = original.copy(
            themeMode = SettingsMapper.themeModeFromString(stored["theme"]),
            accent = SettingsMapper.accentFromString(stored["accent"]),
            sort = original.sort.copy(field = SettingsMapper.sortFieldFromString(stored["sortField"])),
            eq = original.eq.copy(
                bandLevelsMb = SettingsMapper.bandLevelsFromCsv(stored["bands"], -1500, 1500, bandCount = 5),
                presetIndex = SettingsMapper.sanitizePresetIndex(original.eq.presetIndex),
                bassBoostStrength = SettingsMapper.clampStrength(original.eq.bassBoostStrength),
            ),
        )
        assertEquals(original, restored)
    }

    @Test
    fun missingStoredValues_yieldDefaults() {
        val restored = AppSettings(
            themeMode = SettingsMapper.themeModeFromString(null),
            accent = SettingsMapper.accentFromString(null),
            sort = SortSpec(SettingsMapper.sortFieldFromString(null), true),
            eq = EqSettings(bandLevelsMb = SettingsMapper.bandLevelsFromCsv(null, -1500, 1500)),
        )
        assertEquals(AppSettings(), restored)
    }
}
