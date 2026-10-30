@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.localmusic.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localmusic.settings.Accent
import dev.localmusic.settings.ThemeMode
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.SwitchRow
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.theme.accentSwatch
import kotlinx.coroutines.launch

/**
 * Theme settings (spec §7/§10): radio rows "Follow system" / "Light" / "Dark", switches "Pure black" and
 * "Dynamic color" (API 31+ only), accent swatches "Accent <Name>". Every change is persisted and applied live by
 * LocalMusicTheme. Picking an accent also turns Dynamic color off (otherwise the accent would have no effect).
 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.settingsRepository
    val settings by repo.state.collectAsStateWithLifecycle()
    val launch: (suspend () -> Unit) -> Unit = { block -> container.appScope.launch { block() } }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
                if (dynamicSupported && settings.dynamicColor) "Accent (picking one turns Dynamic color off)" else "Accent",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (accent in Accent.entries) {
                    val selected = settings.accent == accent && !(dynamicSupported && settings.dynamicColor)
                    AccentSwatch(accent, selected) {
                        launch {
                            repo.setAccent(accent)
                            if (dynamicSupported && settings.dynamicColor) repo.setDynamicColor(false)
                        }
                    }
                }
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

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accentSwatch(accent))
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                )
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = "Accent ${accent.label}" },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
        }
        Text(accent.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}
