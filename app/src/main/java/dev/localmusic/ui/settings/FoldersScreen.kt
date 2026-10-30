@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.localmusic.AppContainer
import dev.localmusic.ui.components.ActionIcon
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.SwitchRow
import dev.localmusic.ui.components.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(private val container: AppContainer) : ViewModel() {
    private val library = container.libraryRepository

    val folders = library.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scanState = library.scanState
    val accessLost = library.accessLost

    fun add(uri: Uri) {
        container.appScope.launch { library.addFolder(uri) }
    }

    fun remove(id: Long) {
        container.appScope.launch { library.removeFolder(id) }
    }

    fun setIncludeSubfolders(id: Long, include: Boolean) {
        container.appScope.launch { library.setIncludeSubfolders(id, include) }
    }

    fun rescan() {
        library.rescanAllAsync()
    }
}

/** settings/folders (spec §10): Add folder, per-folder Include subfolders + Remove folder, Rescan + progress. */
@Composable
fun FoldersScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: FoldersViewModel = viewModel(factory = viewModelFactory { initializer { FoldersViewModel(container) } })
    val folders by vm.folders.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val lost by vm.accessLost.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.add(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music folders") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    ActionIcon(Icons.Filled.Refresh, "Rescan", vm::rescan, enabled = !scan.running)
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add folder") },
                // BUG-2: label the clickable FAB node itself (uiautomator showed it as an unlabelled NAF View).
                modifier = Modifier
                    .testTag("add_folder_fab")
                    .semantics { contentDescription = "Add folder" },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (scan.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Scanning… ${scan.scannedCount} files",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("scan_progress"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (scan.scannedCount > 0) {
                Text(
                    "Last scan: ${scan.scannedCount} files",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("scan_progress"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val list = folders
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState("No music folders yet.\nTap \"Add folder\" to pick one.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { f ->
                        Column(Modifier.testTag("music_folder_row")) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text(f.displayName, style = MaterialTheme.typography.titleMedium)
                                    if (f.id in lost) {
                                        Text(
                                            "Access lost",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    } else {
                                        Text(
                                            Uri.decode(Uri.parse(f.treeUri).lastPathSegment ?: f.treeUri),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                ActionIcon(Icons.Filled.Delete, "Remove folder", { vm.remove(f.id) })
                            }
                            SwitchRow(
                                label = "Include subfolders",
                                checked = f.includeSubfolders,
                                onCheckedChange = { vm.setIncludeSubfolders(f.id, it) },
                                modifier = Modifier.padding(start = 40.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
