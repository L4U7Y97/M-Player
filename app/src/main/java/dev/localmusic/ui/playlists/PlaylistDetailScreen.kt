@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.localmusic.AppContainer
import dev.localmusic.data.repo.PlaylistTrack
import dev.localmusic.ui.components.ActionIcon
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.MiniPlayer
import dev.localmusic.ui.components.RowAction
import dev.localmusic.ui.components.TrackRow
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.components.formatDuration
import dev.localmusic.ui.detail.PlayButtonsRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(private val container: AppContainer, private val playlistId: Long) : ViewModel() {
    private val repo = container.playlistRepository

    val name = repo.playlist(playlistId).map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries = repo.entries(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Plays the playable entries; missing ones are skipped. [fromIndex] is an entry index. */
    fun play(fromIndex: Int, shuffle: Boolean = false) {
        val list = entries.value ?: return
        val playable = list.withIndex().filter { !it.value.missing }
        if (playable.isEmpty()) return
        val tracks = playable.map { it.value.track!! }
        val start = if (shuffle) {
            tracks.indices.random()
        } else {
            playable.indexOfFirst { it.index >= fromIndex }.coerceAtLeast(0)
        }
        container.playerConnection.playTracks(tracks, start, shuffle)
    }

    fun addToQueue(entry: PlaylistTrack) {
        entry.track?.takeIf { !entry.missing }?.let { container.playerConnection.addToQueue(listOf(it)) }
    }

    fun move(from: Int, to: Int) {
        container.appScope.launch { repo.move(playlistId, from, to) }
    }

    fun remove(index: Int) {
        container.appScope.launch { repo.remove(playlistId, index) }
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: Long, onBack: () -> Unit, onOpenNowPlaying: () -> Unit) {
    val container = appContainer()
    val vm: PlaylistDetailViewModel = viewModel(
        key = "playlist-$playlistId",
        factory = viewModelFactory { initializer { PlaylistDetailViewModel(container, playlistId) } },
    )
    val name by vm.name.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = { MiniPlayer(onOpen = onOpenNowPlaying) },
    ) { padding ->
        val list = entries
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState("This playlist is empty.\nUse \"Add to playlist\" on any track.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        PlayButtonsRow(
                            onPlayAll = { vm.play(0) },
                            onShuffleAll = { vm.play(0, shuffle = true) },
                            enabled = list.any { !it.missing },
                        )
                    }
                    itemsIndexed(list, key = { _, e -> e.entryId }) { index, e ->
                        val t = e.track
                        TrackRow(
                            title = t?.title ?: e.trackId.substringAfterLast('|').substringAfterLast('/'),
                            subtitle = if (t != null) "${t.artist} · ${formatDuration(t.durationMs)}" else "",
                            missing = e.missing,
                            onClick = {
                                vm.play(index)
                                if (!e.missing) onOpenNowPlaying()
                            },
                            menu = if (e.missing) emptyList() else listOf(RowAction("Add to queue") { vm.addToQueue(e) }),
                            trailing = {
                                ActionIcon(
                                    Icons.Filled.KeyboardArrowUp,
                                    "Move up",
                                    { vm.move(index, index - 1) },
                                    enabled = index > 0,
                                )
                                ActionIcon(
                                    Icons.Filled.KeyboardArrowDown,
                                    "Move down",
                                    { vm.move(index, index + 1) },
                                    enabled = index < list.lastIndex,
                                )
                                ActionIcon(Icons.Filled.Close, "Remove from playlist", { vm.remove(index) })
                            },
                        )
                    }
                }
            }
        }
    }
}
