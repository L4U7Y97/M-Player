@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.SwitchRow
import dev.localmusic.ui.components.appContainer
import kotlinx.coroutines.launch

/** Settings root (spec §10): "Music folders", "Equalizer", "Theme", switch "Skip silence". */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenTheme: () -> Unit,
) {
    val container = appContainer()
    val settings by container.settingsRepository.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            NavRow("Music folders", "Pick folders, include subfolders, rescan", Icons.Filled.Folder, onOpenFolders)
            NavRow("Equalizer", "Presets, bands, bass boost", Icons.Filled.Equalizer, onOpenEqualizer)
            NavRow("Theme", "Light, dark, pure black, accent color", Icons.Filled.Palette, onOpenTheme)
            HorizontalDivider()
            SwitchRow(
                label = "Skip silence",
                supporting = "Skip silent parts of tracks while playing",
                checked = settings.skipSilence,
                onCheckedChange = { v -> container.appScope.launch { container.settingsRepository.setSkipSilence(v) } },
            )
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
