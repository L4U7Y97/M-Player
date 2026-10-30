@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.localmusic.AppContainer
import dev.localmusic.domain.SortField
import dev.localmusic.domain.SortSpec
import dev.localmusic.domain.Track
import dev.localmusic.domain.TrackSorter
import dev.localmusic.ui.components.ActionIcon
import dev.localmusic.ui.components.AddToPlaylistDialog
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.MiniPlayer
import dev.localmusic.ui.components.RowAction
import dev.localmusic.ui.components.TrackRow
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.components.formatDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a detail list shows (routes folder/{path}, artist/{name}, album/{album}/{artist}). */
sealed interface TrackSource {
    data class Folder(val path: String) : TrackSource
    data class Artist(val name: String) : TrackSource
    data class Album(val album: String, val artist: String) : TrackSource
}

class TrackListViewModel(private val container: AppContainer, source: TrackSource) : ViewModel() {
    private val library = container.libraryRepository
    private val settings = container.settingsRepository

    private val raw: Flow<List<Track>> = when (source) {
        is TrackSource.Folder -> library.tracksInFolder(source.path)
        is TrackSource.Artist -> library.tracksByArtist(source.name)
        is TrackSource.Album -> library.tracksInAlbum(source.album, source.artist)
    }

    val sort = settings.settings.map { it.sort }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.state.value.sort)

    /** null until first load. */
    val tracks = combine(raw, settings.settings.map { it.sort }) { list, spec -> TrackSorter.sort(list, spec) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(spec: SortSpec) {
        container.appScope.launch { settings.setSort(spec) }
    }

    fun play(index: Int) {
        val list = tracks.value ?: return
        container.playerConnection.playTracks(list, index, shuffle = false)
    }

    fun shuffleAll() {
        val list = tracks.value ?: return
        if (list.isEmpty()) return
        container.playerConnection.playTracks(list, list.indices.random(), shuffle = true)
    }

    fun addToQueue(track: Track) = container.playerConnection.addToQueue(listOf(track))
}

@Composable
fun TrackListScreen(
    source: TrackSource,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val container = appContainer()
    val vm: TrackListViewModel = viewModel(
        key = source.toString(),
        factory = viewModelFactory { initializer { TrackListViewModel(container, source) } },
    )
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var addToPlaylist by remember { mutableStateOf<List<String>?>(null) }

    val (title, subtitle) = when (source) {
        is TrackSource.Folder -> source.path.substringAfterLast('/') to source.path
        is TrackSource.Artist -> source.name to null
        is TrackSource.Album -> source.album to source.artist
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = { SortMenu(sort, vm::setSort) },
            )
        },
        bottomBar = { MiniPlayer(onOpen = onOpenNowPlaying) },
    ) { padding ->
        val list = tracks
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState("No tracks here")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        PlayButtonsRow(onPlayAll = { vm.play(0) }, onShuffleAll = vm::shuffleAll)
                    }
                    itemsIndexed(list, key = { _, t -> t.id }) { index, t ->
                        TrackRow(
                            title = t.title,
                            subtitle = "${t.artist} · ${formatDuration(t.durationMs)}",
                            onClick = {
                                vm.play(index)
                                onOpenNowPlaying()
                            },
                            menu = listOf(
                                RowAction("Add to queue") { vm.addToQueue(t) },
                                RowAction("Add to playlist") { addToPlaylist = listOf(t.id) },
                            ),
                        )
                    }
                }
            }
        }
    }

    addToPlaylist?.let { ids ->
        AddToPlaylistDialog(trackIds = ids, onDismiss = { addToPlaylist = null })
    }
}

/** "Play all" / "Shuffle all" buttons (spec §10/§11). */
@Composable
fun PlayButtonsRow(onPlayAll: () -> Unit, onShuffleAll: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onPlayAll, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size18())
            Text("Play all")
        }
        FilledTonalButton(onClick = onShuffleAll, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size18())
            Text("Shuffle all")
        }
    }
    HorizontalDivider()
}

private fun Modifier.size18(): Modifier = this.size(ButtonDefaults.IconSize)

private val FIELD_LABELS = listOf(
    SortField.TRACK_NUMBER to "Track number",
    SortField.TITLE to "Title",
    SortField.FILE_NAME to "File name",
    SortField.DATE_ADDED to "Date added",
)

/** "Sort" menu: 4 fields + Ascending / Descending; current choices checked. Persisted globally. */
@Composable
fun SortMenu(sort: SortSpec, onChange: (SortSpec) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionIcon(Icons.AutoMirrored.Filled.Sort, "Sort", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FIELD_LABELS.forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = { if (sort.field == field) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                    onClick = {
                        open = false
                        onChange(sort.copy(field = field))
                    },
                )
            }
            HorizontalDivider()
            listOf(true to "Ascending", false to "Descending").forEach { (asc, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = { if (sort.ascending == asc) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                    onClick = {
                        open = false
                        onChange(sort.copy(ascending = asc))
                    },
                )
            }
        }
    }
}
