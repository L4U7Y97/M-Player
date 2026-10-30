package dev.localmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.localmusic.MainActivity
import dev.localmusic.appContainer
import dev.localmusic.domain.PlaybackRestore
import dev.localmusic.domain.QueueModel
import dev.localmusic.domain.RepeatCycler
import dev.localmusic.widget.WidgetRenderer
import dev.localmusic.widget.WidgetState
import dev.localmusic.widget.WidgetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Media3 session service (spec §5). The default DefaultMediaNotificationProvider gives the media notification and
 * lock-screen controls; the MediaSession routes Bluetooth / headset media buttons.
 *
 * The queue, current item / position, shuffle (with its order) and repeat are saved to [PlaybackStateStore] and
 * restored, paused, when the service starts again.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastWidgetState: WidgetState? = null
    private val stateStore by lazy { PlaybackStateStore(this) }

    /** True until the saved state has been restored (or skipped); nothing is saved meanwhile. */
    private var restoring = true

    override fun onCreate() {
        super.onCreate()
        val container = appContainer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setAudioSessionId(container.audioSessionId)
        player.addListener(LoggingListener)
        player.setShuffleOrder(AppendingShuffleOrder(emptyList()))
        player.addListener(ShuffleFromCurrentListener(player))
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                pushWidgetState(player)
                if (restoring) return
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                    saveQueue(player)
                }
                savePosition(player)
            }
        })
        // Equalizer / bass boost live on the same audio session id (spec §7); start() re-applies saved settings.
        container.audioEffects.start()

        val activityIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback)
            .build()

        // Phase 6 hook (T27): skip silence follows the persisted setting.
        scope.launch {
            container.settingsRepository.settings
                .map { it.skipSilence }
                .distinctUntilChanged()
                .collect { enabled ->
                    player.skipSilenceEnabled = enabled
                    Log.d(TAG, "skipSilenceEnabled = $enabled")
                }
        }
        scope.launch { restoreState(player) }
        scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (!restoring && player.isPlaying) savePosition(player)
            }
        }
        Log.d(TAG, "PlaybackService created (audioSessionId=${container.audioSessionId})")
        pushWidgetState(player)
    }

    /**
     * Rebuilds the saved queue (tracks that are gone or missing are dropped) at the saved item and position, with
     * shuffle order and repeat mode, prepared but paused. Skipped if something started playing meanwhile.
     */
    private suspend fun restoreState(player: ExoPlayer) {
        try {
            val snapshot = stateStore.read() ?: return
            val tracks = appContainer.libraryRepository.tracksByIds(snapshot.trackIds.distinct()).associateBy { it.id }
            val restored = PlaybackRestore.restore(snapshot) { tracks[snapshot.trackIds[it]]?.missing == false }
                ?: return
            if (session == null || player.mediaItemCount > 0) return
            val items = restored.kept.map { MediaItemMapper.toMediaItem(tracks.getValue(snapshot.trackIds[it])) }
            player.repeatMode = RepeatCycler.toPlayerMode(snapshot.repeatMode)
            // Enabled while the queue is still empty, so ShuffleFromCurrentListener doesn't replace the saved order.
            player.shuffleModeEnabled = snapshot.shuffle
            player.setMediaItems(items, restored.currentIndex, restored.positionMs)
            if (snapshot.shuffle) player.setShuffleOrder(AppendingShuffleOrder(restored.shuffleOrder))
            player.prepare()
            Log.d(TAG, "restored ${items.size}/${snapshot.trackIds.size} items at ${restored.currentIndex}")
        } finally {
            restoring = false
            if (session != null) {
                saveQueue(player)
                savePosition(player)
            }
        }
    }

    private fun saveQueue(player: Player) {
        val ids = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        val order = ArrayList<Int>()
        val timeline = player.currentTimeline
        if (player.shuffleModeEnabled && !timeline.isEmpty) {
            var i = timeline.getFirstWindowIndex(true)
            while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
                order += i
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
            }
        }
        stateStore.writeQueue(ids, order)
    }

    private fun savePosition(player: Player) {
        stateStore.writePosition(
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = RepeatCycler.fromPlayerMode(player.repeatMode),
        )
    }

    /** Writes the widget state and re-renders placed widgets when what they show changed (spec §8). */
    private fun pushWidgetState(player: Player) {
        val item = player.currentMediaItem
        val meta = player.mediaMetadata
        val state = if (player.mediaItemCount == 0 || item == null) {
            WidgetState()
        } else {
            WidgetState(
                active = true,
                title = (meta.title ?: item.mediaMetadata.title)?.toString().orEmpty(),
                artist = (meta.artist ?: item.mediaMetadata.artist)?.toString().orEmpty(),
                isPlaying = player.playWhenReady &&
                    player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
            )
        }
        if (state == lastWidgetState) return
        lastWidgetState = state
        WidgetStateStore.write(this, state)
        WidgetRenderer.updateAll(this, state)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player != null && !restoring) savePosition(player)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.let {
            if (!restoring) savePosition(it.player)
            it.player.release()
            it.release()
        }
        session = null
        // Service gone: the widget falls back to "Not playing" (its buttons then open the app).
        WidgetStateStore.write(this, WidgetState())
        WidgetRenderer.updateAll(this, WidgetState())
        Log.d(TAG, "PlaybackService destroyed")
        super.onDestroy()
    }

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(MediaItemMapper::restoreUri).toMutableList())
    }

    /**
     * Whenever shuffle is switched on (Now Playing toggle, or "Shuffle all" which sets the items first and then
     * enables shuffle), install a shuffle order that starts with the current item and contains every other item
     * once. Media3's default order is random over the whole list, so the current item could sit mid-order and,
     * with repeat off, the items before it would never play. The order is an [AppendingShuffleOrder], so items added
     * later are appended to the end of the play order instead of landing at a random position (BUG-1).
     */
    private class ShuffleFromCurrentListener(private val player: ExoPlayer) : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (!shuffleModeEnabled) return
            val count = player.mediaItemCount
            if (count <= 1) return
            val order = QueueModel.shuffledOrderStartingWith(count, player.currentMediaItemIndex)
            // AppendingShuffleOrder (not DefaultShuffleOrder): later "Add to queue" items go to the end of the play
            // order, so they play after the current item (BUG-1).
            player.setShuffleOrder(AppendingShuffleOrder(order))
            Log.d(TAG, "shuffle order starts at ${player.currentMediaItemIndex} ($count items)")
        }
    }

    private object LoggingListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "playbackState = $playbackState")
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "player error: ${error.errorCodeName}", error)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "transition -> ${mediaItem?.mediaId} (reason $reason)")
        }
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val SAVE_INTERVAL_MS = 10_000L
    }
}
