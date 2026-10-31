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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import dev.localmusic.settings.AppSettings
import dev.localmusic.settings.ColorTheme
import dev.localmusic.settings.ThemeMode

private data class SeedTones(val lightPrimary: Color, val lightContainer: Color, val darkPrimary: Color, val darkContainer: Color)

/** Hand-tuned primary / primaryContainer of the seed themes; null for Purple (Material's baseline) and the palettes. */
private fun seedTones(theme: ColorTheme): SeedTones? = when (theme) {
    ColorTheme.BLUE -> SeedTones(Color(0xFF1F5FA8), Color(0xFFD5E3FF), Color(0xFFA7C8FF), Color(0xFF00468A))
    ColorTheme.GREEN -> SeedTones(Color(0xFF2E6B30), Color(0xFFB0F2A8), Color(0xFF95D78E), Color(0xFF13521B))
    ColorTheme.ORANGE -> SeedTones(Color(0xFF9A4600), Color(0xFFFFDBC8), Color(0xFFFFB68A), Color(0xFF753400))
    ColorTheme.PINK -> SeedTones(Color(0xFFA0306A), Color(0xFFFFD8E7), Color(0xFFFFB0D0), Color(0xFF7F1A52))
    else -> null
}

/**
 * Full color scheme of [theme] (spec §7). Purple is Material's baseline scheme. Blue / Green / Orange / Pink set every
 * accent-dependent role (on*, containers, secondary, tertiary, inversePrimary, tinted neutrals) from their seed via
 * [ColorRoles.fromSeed] (BUG-3), with the hand-tuned primary / primaryContainer kept on top. The other themes map a
 * hand-picked palette ([ThemePalettes]) to every role, error included, via [ColorRoles.fromPalette]. The *Fixed roles
 * keep Material's baseline in every theme: neither the app nor Material 3 1.4's components draw with them.
 */
fun colorThemeScheme(theme: ColorTheme, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    ThemePalettes.of(theme)?.let { return base.withRoles(ColorRoles.fromPalette(it.variant(dark), dark)) }
    val s = seedTones(theme) ?: return base
    val primary = if (dark) s.darkPrimary else s.lightPrimary
    return base.withRoles(ColorRoles.fromSeed(s.lightPrimary.toArgb(), dark)).copy(
        primary = primary,
        primaryContainer = if (dark) s.darkContainer else s.lightContainer,
        inversePrimary = if (dark) s.lightPrimary else s.darkPrimary,
        surfaceTint = primary,
    )
}

/** Pure black (dark only): background, surface and the lowest surface containers turn black. */
fun ColorScheme.withPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
)

/** The non-dynamic scheme for [theme] in [dark], with pure black applied when [pureBlack] (dark only). */
fun appColorScheme(theme: ColorTheme, dark: Boolean, pureBlack: Boolean): ColorScheme =
    colorThemeScheme(theme, dark).let { if (dark && pureBlack) it.withPureBlack() else it }

private fun ColorScheme.withRoles(r: ColorRoles): ColorScheme = copy(
    primary = Color(r.primary),
    onPrimary = Color(r.onPrimary),
    primaryContainer = Color(r.primaryContainer),
    onPrimaryContainer = Color(r.onPrimaryContainer),
    inversePrimary = Color(r.inversePrimary),
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
    surfaceTint = Color(r.primary),
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
    error = r.error?.let(::Color) ?: error,
    onError = r.onError?.let(::Color) ?: onError,
    errorContainer = r.errorContainer?.let(::Color) ?: errorContainer,
    onErrorContainer = r.onErrorContainer?.let(::Color) ?: onErrorContainer,
)

/**
 * Spec §7: dark from mode or system; dynamic color on API 31+ when enabled, else the color theme's scheme; pure black
 * (dark only) turns background and surface black. Applies live.
 */
@Composable
fun LocalMusicTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (dark && settings.pureBlack) dynamic.withPureBlack() else dynamic
    } else {
        remember(settings.colorTheme, dark, settings.pureBlack) {
            appColorScheme(settings.colorTheme, dark, settings.pureBlack)
        }
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
