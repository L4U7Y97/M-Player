@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localmusic.domain.RepeatCycler
import dev.localmusic.domain.RepeatMode
import dev.localmusic.ui.components.ActionIcon
import dev.localmusic.ui.components.AddToPlaylistDialog
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.components.formatDuration

/** Now Playing (spec §10/§11). Reads the PlayerConnection state directly (it is the state holder). */
@Composable
fun NowPlayingScreen(onBack: () -> Unit, onOpenQueue: () -> Unit) {
    val connection = appContainer().playerConnection
    val state by connection.state.collectAsStateWithLifecycle()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var addToPlaylist by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now playing") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (!state.hasMedia) {
            EmptyState("Nothing is playing", Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(state.artist, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
            if (state.album.isNotEmpty()) {
                Text(
                    state.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(16.dp))

            val duration = state.durationMs.coerceAtLeast(0)
            val position = dragValue?.toLong() ?: state.positionMs.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
            Slider(
                value = if (duration > 0) position.toFloat() else 0f,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { connection.seekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                enabled = duration > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Seek bar" },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position), style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Description on the toggle itself so uiautomator reports checked=true/false on the "Shuffle" node.
                IconToggleButton(
                    checked = state.shuffle,
                    onCheckedChange = { connection.toggleShuffle() },
                    modifier = Modifier.semantics { contentDescription = "Shuffle" },
                ) {
                    ToggleIcon(Icons.Filled.Shuffle, on = state.shuffle)
                }
                ActionIcon(Icons.Filled.SkipPrevious, "Previous", connection::previous, iconSize = 36.dp)
                val playDesc = if (state.isPlaying) "Pause" else "Play"
                FilledIconButton(
                    onClick = connection::togglePlayPause,
                    modifier = Modifier.size(72.dp).semantics { contentDescription = playDesc },
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
                ActionIcon(Icons.Filled.SkipNext, "Next", connection::next, iconSize = 36.dp)
                IconButton(
                    onClick = connection::cycleRepeat,
                    modifier = Modifier.semantics { contentDescription = RepeatCycler.label(state.repeatMode) },
                ) {
                    ToggleIcon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        on = state.repeatMode != RepeatMode.OFF,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionIcon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", onOpenQueue)
                ActionIcon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    "Add to playlist",
                    { addToPlaylist = state.mediaId },
                    enabled = state.mediaId != null,
                )
            }
        }
    }

    addToPlaylist?.let { id ->
        AddToPlaylistDialog(trackIds = listOf(id), onDismiss = { addToPlaylist = null })
    }
}

/**
 * A Shuffle / Repeat icon: primary with a dot under it while [on]. The dot keeps the state visible when the primary is
 * close to the text color (Monochrome) and to anyone who can't tell the two colors apart.
 */
@Composable
private fun ToggleIcon(icon: ImageVector, on: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (on) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        if (on) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 6.dp)
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
