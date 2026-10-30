package dev.localmusic.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.localmusic.domain.RepeatCycler
import dev.localmusic.domain.RepeatMode
import dev.localmusic.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One queue row. [index] is the timeline (insertion) index used for move/remove/jump commands. */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
)

data class PlayerUiState(
    val connected: Boolean = false,
    /** True once the player has at least one item (mini player visible). */
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    /** True when the UI should show "Pause" (playWhenReady and not ended/idle). */
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Queue in play order (shuffle-aware, repeat ignored). */
    val queue: List<QueueItem> = emptyList(),
    /** Timeline index of the current item, -1 if none. */
    val currentIndex: Int = -1,
    /**
     * Player is in STATE_ENDED (end of queue reached, or the current item was removed while it was the last one;
     * ExoPlayer then parks on item 0). The UI must not mark any queue row as "Now playing" in that state.
     */
    val ended: Boolean = false,
)

/**
 * Process-wide MediaController wrapper (spec §5). MainActivity calls [connect] in onStart and [disconnect] in
 * onStop (ref-counted). All calls must happen on the main thread.
 *
 * Gapless rule: a list starts with exactly one setMediaItems + prepare + play; queue edits use move/remove/add.
 */
class PlayerConnection(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var users = 0
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }
    }

    fun connect() {
        users++
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        scope.launch {
            val c = try {
                f.await()
            } catch (e: Exception) {
                Log.w(TAG, "MediaController connection failed: ${e.message}")
                if (future === f) future = null
                return@launch
            }
            if (future !== f) return@launch // disconnected meanwhile; releaseFuture already released it
            controller = c
            c.addListener(listener)
            refresh()
        }
    }

    fun disconnect() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        pollJob?.cancel()
        pollJob = null
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Runs [block] with the controller, waiting for a pending connection if needed. Dropped if disconnected. */
    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        val f = future
        if (f == null) {
            Log.w(TAG, "command dropped: not connected")
            return
        }
        scope.launch {
            val c = try {
                f.await()
            } catch (e: Exception) {
                return@launch
            }
            block(c)
        }
    }

    // ---- commands -------------------------------------------------------------------------------------

    /** Plays [tracks] starting at [startIndex]; missing tracks must already be filtered out by the caller. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val items = tracks.map(MediaItemMapper::toMediaItem)
        val start = startIndex.coerceIn(0, items.lastIndex)
        withController { c ->
            // Shuffle is switched on only after the items are set: PlaybackService reacts to shuffle being enabled
            // by installing a shuffle order that starts at the current item, so with repeat off every track plays
            // exactly once ("Shuffle all" fix).
            c.shuffleModeEnabled = false
            c.setMediaItems(items, start, 0L)
            Log.d(TAG, "prepare() with ${items.size} items, start=$start, shuffle=$shuffle")
            c.prepare()
            c.play()
            if (shuffle) c.shuffleModeEnabled = true
        }
    }

    fun togglePlayPause() = withController { c ->
        if (showPause(c)) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekToDefaultPosition()
            }
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { it.repeatMode = RepeatCycler.nextPlayerMode(it.repeatMode) }

    /** Jumps to the item at timeline [index]. */
    fun playQueueIndex(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount) return@withController
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        val n = c.mediaItemCount
        if (from in 0 until n && to in 0 until n && from != to) c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Appends to the queue; an empty queue starts playing the tracks instead. */
    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                playTracks(tracks, 0, false)
            } else {
                c.addMediaItems(tracks.map(MediaItemMapper::toMediaItem))
            }
        }
    }

    // ---- state ----------------------------------------------------------------------------------------

    private fun showPause(c: Player): Boolean =
        c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE

    private fun refresh() {
        val c = controller ?: return
        val shuffle = c.shuffleModeEnabled
        val timeline = c.currentTimeline
        val order = ArrayList<Int>()
        if (!timeline.isEmpty) {
            var i = timeline.getFirstWindowIndex(shuffle)
            while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
                order += i
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
            }
        }
        val count = c.mediaItemCount
        val queue = order.filter { it < count }.map { idx ->
            val item = c.getMediaItemAt(idx)
            QueueItem(
                index = idx,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
            )
        }
        val meta = c.mediaMetadata
        val current = c.currentMediaItem
        val duration = c.duration.let { if (it == C.TIME_UNSET || it < 0) 0L else it }
        val playing = showPause(c)
        _state.value = PlayerUiState(
            connected = true,
            hasMedia = count > 0,
            mediaId = current?.mediaId,
            title = meta.title?.toString() ?: current?.mediaMetadata?.title?.toString().orEmpty(),
            artist = meta.artist?.toString() ?: current?.mediaMetadata?.artist?.toString().orEmpty(),
            album = meta.albumTitle?.toString() ?: current?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            isPlaying = playing,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            shuffle = shuffle,
            repeatMode = RepeatCycler.fromPlayerMode(c.repeatMode),
            queue = queue,
            currentIndex = if (count > 0) c.currentMediaItemIndex else -1,
            ended = c.playbackState == Player.STATE_ENDED,
        )
        updatePolling(c.isPlaying)
    }

    private fun updatePolling(playing: Boolean) {
        if (playing && pollJob?.isActive != true) {
            pollJob = scope.launch {
                while (isActive) {
                    delay(POLL_MS)
                    val c = controller ?: break
                    val duration = c.duration.let { if (it == C.TIME_UNSET || it < 0) 0L else it }
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = duration,
                    )
                }
            }
        } else if (!playing) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    private companion object {
        const val TAG = "PlayerConnection"
        const val POLL_MS = 500L
    }
}
