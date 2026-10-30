package dev.localmusic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import dev.localmusic.settings.Accent
import dev.localmusic.settings.AppSettings
import dev.localmusic.settings.ThemeMode

private data class AccentSeeds(val lightPrimary: Color, val lightContainer: Color, val darkPrimary: Color, val darkContainer: Color)

private fun seeds(accent: Accent): AccentSeeds = when (accent) {
    Accent.PURPLE -> AccentSeeds(Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFFD0BCFF), Color(0xFF4F378B))
    Accent.BLUE -> AccentSeeds(Color(0xFF1F5FA8), Color(0xFFD5E3FF), Color(0xFFA7C8FF), Color(0xFF00468A))
    Accent.GREEN -> AccentSeeds(Color(0xFF2E6B30), Color(0xFFB0F2A8), Color(0xFF95D78E), Color(0xFF13521B))
    Accent.ORANGE -> AccentSeeds(Color(0xFF9A4600), Color(0xFFFFDBC8), Color(0xFFFFB68A), Color(0xFF753400))
    Accent.PINK -> AccentSeeds(Color(0xFFA0306A), Color(0xFFFFD8E7), Color(0xFFFFB0D0), Color(0xFF7F1A52))
}

/**
 * Full color scheme for [accent] (BUG-3). Purple is Material's baseline scheme. The other accents set every
 * accent-dependent role (on*, containers, secondary, tertiary, inversePrimary, tinted neutrals) from the accent seed via
 * [AccentRoles.fromSeed], so switch thumbs, slider tracks, FABs, tab indicators and surfaces all follow the accent;
 * the hand-tuned primary / primaryContainer seeds are kept on top.
 */
fun accentScheme(accent: Accent, dark: Boolean): ColorScheme {
    if (accent == Accent.PURPLE) return if (dark) darkColorScheme() else lightColorScheme()
    val s = seeds(accent)
    val r = AccentRoles.fromSeed(s.lightPrimary.toArgb(), dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = if (dark) s.darkPrimary else s.lightPrimary,
        onPrimary = Color(r.onPrimary),
        primaryContainer = if (dark) s.darkContainer else s.lightContainer,
        onPrimaryContainer = Color(r.onPrimaryContainer),
        inversePrimary = if (dark) s.lightPrimary else s.darkPrimary,
        secondary = Color(r.secondary),
        onSecondary = Color(r.onSecondary),
        secondaryContainer = Color(r.secondaryContainer),
        onSecondaryContainer = Color(r.onSecondaryContainer),
        tertiary = Color(r.tertiary),
        onTertiary = Color(r.onTertiary),
        tertiaryContainer = Color(r.tertiaryContainer),
        onTertiaryContainer = Color(r.onTertiaryContainer),
        background = Color(r.background),
        onBackground = Color(r.onBackground),
        surface = Color(r.surface),
        onSurface = Color(r.onSurface),
        surfaceVariant = Color(r.surfaceVariant),
        onSurfaceVariant = Color(r.onSurfaceVariant),
        surfaceTint = if (dark) s.darkPrimary else s.lightPrimary,
        inverseSurface = Color(r.inverseSurface),
        inverseOnSurface = Color(r.inverseOnSurface),
        outline = Color(r.outline),
        outlineVariant = Color(r.outlineVariant),
        surfaceDim = Color(r.surfaceDim),
        surfaceBright = Color(r.surfaceBright),
        surfaceContainerLowest = Color(r.surfaceContainerLowest),
        surfaceContainerLow = Color(r.surfaceContainerLow),
        surfaceContainer = Color(r.surfaceContainer),
        surfaceContainerHigh = Color(r.surfaceContainerHigh),
        surfaceContainerHighest = Color(r.surfaceContainerHighest),
    )
}

/** Swatch color for the accent pickers on the Theme screen. */
fun accentSwatch(accent: Accent): Color = seeds(accent).lightPrimary

/**
 * Spec §7: dark from mode or system; dynamic color on API 31+ when enabled, else accent scheme; pure black (dark
 * only) turns background and surface black. Applies live.
 */
@Composable
fun LocalMusicTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    var scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme(settings.accent, dark)
    }
    if (dark && settings.pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Whether the app renders dark for these settings (mode, or the system setting for "Follow system"). */
@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
