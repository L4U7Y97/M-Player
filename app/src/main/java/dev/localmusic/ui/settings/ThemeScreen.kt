@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localmusic.settings.ColorTheme
import dev.localmusic.settings.ThemeMode
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.SwitchRow
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.theme.appColorScheme
import dev.localmusic.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.launch

/**
 * Theme settings (spec §7/§10): radio rows "Follow system" / "Light" / "Dark", switches "Pure black" and
 * "Dynamic color" (API 31+ only), and color theme cards "Theme <Name>", each a small preview of that theme in the current
 * mode (and pure black). Every change is persisted and applied live by LocalMusicTheme. Picking a theme also turns
 * Dynamic color off (otherwise the theme would have no effect); while Dynamic color is on no card is selected.
 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.settingsRepository
    val settings by repo.state.collectAsStateWithLifecycle()
    val launch: (suspend () -> Unit) -> Unit = { block -> container.appScope.launch { block() } }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicOn = dynamicSupported && settings.dynamicColor
    val dark = isAppInDarkTheme(settings)
    val previews = remember(dark, settings.pureBlack) {
        ColorTheme.entries.associateWith { appColorScheme(it, dark, settings.pureBlack) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Theme") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Mode")
            for ((mode, label) in listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")) {
                RadioRow(label, selected = settings.themeMode == mode) { launch { repo.setThemeMode(mode) } }
            }
            SwitchRow(
                label = "Pure black",
                supporting = "Black background in dark mode",
                checked = settings.pureBlack,
                onCheckedChange = { v -> launch { repo.setPureBlack(v) } },
            )
            HorizontalDivider()
            SectionTitle("Colors")
            if (dynamicSupported) {
                SwitchRow(
                    label = "Dynamic color",
                    supporting = "Use colors from your wallpaper",
                    checked = settings.dynamicColor,
                    onCheckedChange = { v -> launch { repo.setDynamicColor(v) } },
                )
            }
            Text(
                if (dynamicOn) "Color theme (picking one turns Dynamic color off)" else "Color theme",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ThemeGallery(previews, selected = settings.colorTheme.takeUnless { dynamicOn }) { theme ->
                launch { repo.setColorTheme(theme) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private val MinCardWidth = 96.dp
private val CardGap = 12.dp

/**
 * Theme cards in rows of equal-width columns: as many as fit at [MinCardWidth] scaled with the font size, at least 2
 * (3 on a typical phone at the default size). The grid's shape is given to accessibility services by hand: with one Row
 * per grid row, selectableGroup() would number the cards within their row only.
 */
@Composable
private fun ThemeGallery(previews: Map<ColorTheme, ColorScheme>, selected: ColorTheme?, onPick: (ColorTheme) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        val minCard = MinCardWidth * LocalDensity.current.fontScale.coerceIn(1f, 2f)
        val columns = ((maxWidth + CardGap) / (minCard + CardGap)).toInt().coerceAtLeast(2)
        val rows = ColorTheme.entries.chunked(columns)
        Column(
            Modifier.semantics { collectionInfo = CollectionInfo(rowCount = rows.size, columnCount = columns) },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                    row.forEachIndexed { c, theme ->
                        ThemeCard(
                            theme,
                            previews.getValue(theme),
                            theme == selected,
                            Modifier
                                .weight(1f)
                                .semantics { collectionItemInfo = CollectionItemInfo(r, 1, c, 1) },
                        ) { onPick(theme) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One selectable theme (role RadioButton, contentDescription "Theme <Name>"): its preview and name. */
@Composable
private fun ThemeCard(theme: ColorTheme, scheme: ColorScheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Theme ${theme.label}" }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThemePreview(scheme, selected)
        Text(
            theme.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp)
                .clearAndSetSemantics {},
        )
    }
}

/** A miniature Library screen in [c]: title, tabs, three rows (the first playing), the FAB and the mini player. */
@Composable
private fun ThemePreview(c: ColorScheme, selected: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(shape)
            .background(c.background)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Line(c.onSurface, 0.55f, 5.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(3.dp).background(c.primary, CircleShape))
                repeat(3) { Spacer(Modifier.weight(1f)) }
            }
            repeat(3) { i ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(if (i == 0) c.primaryContainer else c.surfaceContainerHighest, RoundedCornerShape(3.dp)),
                    )
                    Column(Modifier.padding(start = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Line(if (i == 0) c.primary else c.onSurface, 0.8f, 3.dp)
                        Line(c.onSurfaceVariant, 0.5f, 3.dp)
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 30.dp)
                .size(16.dp)
                .background(c.primaryContainer, RoundedCornerShape(5.dp)),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(c.surfaceColorAtElevation(3.dp))
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Line(c.onSurface, 0.7f, 3.dp)
                Line(c.onSurfaceVariant, 0.45f, 3.dp)
            }
            Box(Modifier.size(12.dp).background(c.primary, CircleShape))
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun Line(color: Color, fraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .background(color, CircleShape),
    )
}
