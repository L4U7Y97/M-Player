@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localmusic.playback.EqCapabilities
import dev.localmusic.settings.EqSettings
import dev.localmusic.settings.SettingsMapper
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.SwitchRow
import dev.localmusic.ui.components.appContainer
import kotlinx.coroutines.launch

/**
 * Equalizer settings (spec §7/§10): switch "Equalizer enabled", "Preset" dropdown (device presets + "Custom"),
 * one slider per band ("Band <freq> Hz"), switch "Bass boost", slider "Bass boost strength". Everything is
 * persisted through SettingsRepository and applied by AudioEffectsController. Devices without audio effects
 * get the text "Equalizer not available on this device" instead of a crash.
 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val container = appContainer()
    val effects = container.audioEffects
    val repo = container.settingsRepository
    LaunchedEffect(Unit) { effects.start() }
    val fx by effects.state.collectAsStateWithLifecycle()
    val settings by repo.state.collectAsStateWithLifecycle()
    val eq = settings.eq
    val update: ((EqSettings) -> EqSettings) -> Unit = { t -> container.appScope.launch { repo.updateEq(t) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Equalizer") }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        val caps = fx.caps
        when {
            !fx.checked -> EmptyState("Loading…", Modifier.padding(padding))
            !fx.equalizerAvailable && !fx.bassBoostAvailable ->
                EmptyState("Equalizer not available on this device", Modifier.padding(padding))
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (fx.equalizerAvailable && caps != null) {
                    SwitchRow(
                        label = "Equalizer enabled",
                        checked = eq.enabled,
                        onCheckedChange = { v -> update { it.copy(enabled = v) } },
                    )
                    PresetPicker(caps, eq.presetIndex) { index ->
                        update { it.copy(presetIndex = index) }
                    }
                    BandSliders(caps, eq, onPreview = effects::previewBand) { band, level ->
                        update { cur ->
                            val levels = List(caps.bandCount) { i ->
                                if (i == band) level else cur.bandLevelsMb.getOrElse(i) { 0 }
                            }
                            cur.copy(presetIndex = EqSettings.CUSTOM_PRESET, bandLevelsMb = levels)
                        }
                    }
                } else {
                    Text(
                        "Equalizer not available on this device",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                if (fx.bassBoostAvailable) {
                    SwitchRow(
                        label = "Bass boost",
                        checked = eq.bassBoostEnabled,
                        onCheckedChange = { v -> update { it.copy(bassBoostEnabled = v) } },
                    )
                    if (fx.bassStrengthSupported) {
                        StrengthSlider(eq.bassBoostStrength, onPreview = effects::previewBassStrength) { s ->
                            update { it.copy(bassBoostStrength = s) }
                        }
                    }
                } else {
                    Text(
                        "Bass boost not available on this device",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetPicker(caps: EqCapabilities, presetIndex: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = caps.presetNames.getOrNull(presetIndex) ?: "Custom"
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { open = true }
                .semantics { contentDescription = "Preset" }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Preset", style = MaterialTheme.typography.bodyLarge)
                Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val options = listOf(EqSettings.CUSTOM_PRESET to "Custom") + caps.presetNames.mapIndexed { i, n -> i to n }
            for ((index, name) in options) {
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = if (index == presetIndex || (index < 0 && presetIndex !in caps.presetNames.indices)) {
                        { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun BandSliders(
    caps: EqCapabilities,
    eq: EqSettings,
    onPreview: (Int, Int) -> Unit,
    onCommit: (Int, Int) -> Unit,
) {
    // Value being dragged (band -> level); persisted on drag end so DataStore isn't written on every pixel.
    var dragging by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val range = caps.minMb.toFloat()..caps.maxMb.toFloat()
    Column(Modifier.padding(vertical = 8.dp)) {
        for (band in 0 until caps.bandCount) {
            val freq = caps.centerFreqsHz.getOrElse(band) { 0 }
            val stored = SettingsMapper.clampBandLevel(eq.bandLevelsMb.getOrElse(band) { 0 }, caps.minMb, caps.maxMb)
            val value = dragging?.takeIf { it.first == band }?.second ?: stored.toFloat()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$freq Hz", modifier = Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = value,
                    onValueChange = { v ->
                        dragging = band to v
                        onPreview(band, v.toInt())
                    },
                    onValueChangeFinished = {
                        val v = dragging?.takeIf { it.first == band }?.second
                        dragging = null
                        if (v != null) onCommit(band, v.toInt())
                    },
                    valueRange = range,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Band $freq Hz" },
                )
                Text(
                    "%+.1f dB".format(value / 100f),
                    modifier = Modifier.width(72.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StrengthSlider(strength: Int, onPreview: (Int) -> Unit, onCommit: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: strength.toFloat()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Bass boost strength: ${(value / 10).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = { v ->
                dragging = v
                onPreview(v.toInt())
            },
            onValueChangeFinished = {
                val v = dragging
                dragging = null
                if (v != null) onCommit(v.toInt())
            },
            valueRange = EqSettings.MIN_STRENGTH.toFloat()..EqSettings.MAX_STRENGTH.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Bass boost strength" },
        )
    }
}
