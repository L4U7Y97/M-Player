@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.localmusic.AppContainer
import dev.localmusic.data.db.AlbumGroup
import dev.localmusic.data.db.ArtistGroup
import dev.localmusic.data.db.FolderGroup
import dev.localmusic.data.db.PlaylistWithCount
import dev.localmusic.ui.components.ActionIcon
import dev.localmusic.ui.components.ConfirmDialog
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.MiniPlayer
import dev.localmusic.ui.components.NameDialog
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.components.trackCountLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val library = container.libraryRepository
    private val playlistsRepo = container.playlistRepository

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val folderGroups = library.folderGroups.state(emptyList())
    val artistGroups = library.artistGroups.state(emptyList())
    val albumGroups = library.albumGroups.state(emptyList())
    val playlists = playlistsRepo.playlists.state(emptyList())
    val folderCount = library.folders.map { it.size }.state(-1)
    val scanState = library.scanState

    fun addFolder(uri: Uri) {
        container.appScope.launch { library.addFolder(uri) }
    }

    fun createPlaylist(name: String) {
        container.appScope.launch { playlistsRepo.create(name) }
    }

    fun renamePlaylist(id: Long, name: String) {
        container.appScope.launch { playlistsRepo.rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        container.appScope.launch { playlistsRepo.delete(id) }
    }
}

private val TABS = listOf("Folders", "Artists", "Albums", "Playlists")

@Composable
fun LibraryScreen(
    onOpenFolder: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val container = appContainer()
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(container) } })
    var tab by rememberSaveable { mutableIntStateOf(container.uiStateStore.libraryTab.coerceIn(TABS.indices)) }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    val folders by vm.folderGroups.collectAsStateWithLifecycle()
    val artists by vm.artistGroups.collectAsStateWithLifecycle()
    val albums by vm.albumGroups.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val folderCount by vm.folderCount.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("M-Player") },
                    actions = {
                        ActionIcon(Icons.Filled.Settings, "Settings", onOpenSettings)
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = {
                                tab = i
                                container.uiStateStore.libraryTab = i
                            },
                            text = { Text(label) },
                        )
                    }
                }
            }
        },
        bottomBar = { MiniPlayer(onOpen = onOpenNowPlaying) },
        floatingActionButton = {
            if (tab == 3) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New playlist") },
                    // BUG-2: label the clickable FAB node itself (uiautomator showed it as an unlabelled NAF View).
                    modifier = Modifier
                        .testTag("new_playlist_fab")
                        .semantics { contentDescription = "New playlist" },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val noFolders = folderCount == 0
            val emptyText = when {
                scan.running -> "Scanning… ${scan.scannedCount} files"
                noFolders -> "No music folders yet"
                else -> "No music found in your folders"
            }
            val addFolderButton: @Composable () -> Unit = {
                Button(onClick = { picker.launch(null) }) { Text("Add folder") }
            }
            when (tab) {
                0 -> GroupList(folders, emptyText, if (noFolders) addFolderButton else null) { g: FolderGroup ->
                    GroupRow("folder_row", g.name, "${g.path} · ${trackCountLabel(g.trackCount)}", Icons.Filled.Folder) {
                        onOpenFolder(g.path)
                    }
                }
                1 -> GroupList(artists, emptyText, if (noFolders) addFolderButton else null) { g: ArtistGroup ->
                    GroupRow("artist_row", g.name, trackCountLabel(g.trackCount), Icons.Filled.Person) { onOpenArtist(g.name) }
                }
                2 -> GroupList(albums, emptyText, if (noFolders) addFolderButton else null) { g: AlbumGroup ->
                    GroupRow("album_row", g.album, "${g.artist} · ${trackCountLabel(g.trackCount)}", Icons.Filled.Album) {
                        onOpenAlbum(g.album, g.artist)
                    }
                }
                else -> PlaylistsTab(
                    playlists = playlists,
                    onOpen = onOpenPlaylist,
                    onRename = vm::renamePlaylist,
                    onDelete = vm::deletePlaylist,
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = {
                vm.createPlaylist(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun <T> GroupList(
    items: List<T>,
    emptyText: String,
    emptyAction: (@Composable () -> Unit)?,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(emptyText, action = emptyAction)
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items) { row(it) }
        }
    }
}

@Composable
private fun GroupRow(
    tag: String,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.testTag(tag).clickable(onClick = onClick),
    )
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistWithCount>,
    onOpen: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var renaming by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var deleting by remember { mutableStateOf<PlaylistWithCount?>(null) }

    if (playlists.isEmpty()) {
        EmptyState("No playlists yet")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(playlists, key = { it.id }) { p ->
                var menuOpen by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(p.name, maxLines = 1) },
                    supportingContent = { Text(trackCountLabel(p.trackCount)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    trailingContent = {
                        Box {
                            ActionIcon(Icons.Filled.MoreVert, "More options", { menuOpen = true })
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Rename playlist") }, onClick = {
                                    menuOpen = false
                                    renaming = p
                                })
                                DropdownMenuItem(text = { Text("Delete playlist") }, onClick = {
                                    menuOpen = false
                                    deleting = p
                                })
                            }
                        }
                    },
                    modifier = Modifier.testTag("playlist_row").clickable { onOpen(p.id) },
                )
            }
        }
    }

    renaming?.let { p ->
        NameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initial = p.name,
            onConfirm = {
                onRename(p.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete playlist",
            text = "Delete \"${p.name}\"? The music files are not touched.",
            confirmLabel = "Delete",
            onConfirm = {
                onDelete(p.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
