@file:OptIn(ExperimentalMaterial3Api::class)

package dev.localmusic.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localmusic.ui.components.ActionIcon
import dev.localmusic.ui.components.BackButton
import dev.localmusic.ui.components.EmptyState
import dev.localmusic.ui.components.TrackRow
import dev.localmusic.ui.components.appContainer

/**
 * Queue in play order (spec §10). Rows: tap = jump; "Move up"/"Move down" (disabled while shuffle is on, since
 * play order != timeline order); "Remove from queue". Current row is highlighted and `selected`.
 */
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val connection = appContainer().playerConnection
    val state by connection.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val pos = state.queue.indexOfFirst { it.index == state.currentIndex }
        if (pos > 0) listState.scrollToItem(pos)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Queue")
                        if (state.shuffle) {
                            Text(
                                "Shuffle is on: reordering disabled",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (state.queue.isEmpty()) {
            EmptyState("The queue is empty", Modifier.padding(padding))
            return@Scaffold
        }
        val canReorder = !state.shuffle
        val count = state.queue.size
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState) {
            itemsIndexed(state.queue, key = { _, q -> "${q.index}|${q.mediaId}" }) { pos, q ->
                // Nothing is "now playing" once the player ENDED (e.g. the current last item was removed and
                // ExoPlayer parked on item 0): no row is highlighted / selected then.
                val current = !state.ended && q.index == state.currentIndex
                TrackRow(
                    title = q.title,
                    subtitle = if (current) "Now playing · ${q.artist}" else q.artist,
                    testTag = "queue_row",
                    highlighted = current,
                    onClick = { connection.playQueueIndex(q.index) },
                    modifier = Modifier
                        .semantics { selected = current }
                        .then(
                            if (current) {
                                Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            } else {
                                Modifier
                            },
                        ),
                    trailing = {
                        ActionIcon(
                            Icons.Filled.KeyboardArrowUp,
                            "Move up",
                            { connection.moveQueueItem(q.index, q.index - 1) },
                            enabled = canReorder && pos > 0,
                        )
                        ActionIcon(
                            Icons.Filled.KeyboardArrowDown,
                            "Move down",
                            { connection.moveQueueItem(q.index, q.index + 1) },
                            enabled = canReorder && pos < count - 1,
                        )
                        ActionIcon(Icons.Filled.Close, "Remove from queue", { connection.removeQueueItem(q.index) })
                    },
                )
            }
        }
    }
}
