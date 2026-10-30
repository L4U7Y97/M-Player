package dev.localmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Text-input dialog ("Playlist name") with confirm label "Create" or "Rename" and "Cancel". */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.testTagsAsResourceId()) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_field")
                    .focusRequester(focus),
            )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Add to playlist" dialog (spec §10): lists playlists (testTag `playlist_choice`) + "New playlist" row.
 * Writes run on the app scope so dismissing the dialog never cancels them.
 */
@Composable
fun AddToPlaylistDialog(trackIds: List<String>, onDismiss: () -> Unit) {
    val container = appContainer()
    val repo = container.playlistRepository
    val playlists by repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by rememberSaveable { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                container.appScope.launch {
                    val id = repo.create(name)
                    repo.addTracks(id, trackIds)
                }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(Modifier.testTagsAsResourceId()) {
                ListItem(
                    headlineContent = { Text("New playlist") },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.clickable { creating = true },
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(trackCountLabel(p.trackCount)) },
                            modifier = Modifier
                                .testTag("playlist_choice")
                                .clickable {
                                    container.appScope.launch { repo.addTracks(p.id, trackIds) }
                                    onDismiss()
                                }
                                .padding(0.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Dialogs/popups are separate windows: re-enable testTag -> resource-id inside them (spec §11). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.testTagsAsResourceId(): Modifier = this.semantics { testTagsAsResourceId = true }
