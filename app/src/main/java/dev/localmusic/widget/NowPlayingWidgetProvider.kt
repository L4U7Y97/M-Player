package dev.localmusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.localmusic.playback.PlaybackService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Home-screen widget (spec §8, RemoteViews). Renders from [WidgetStateStore]; PlaybackService pushes updates on
 * track / play-state changes.
 *
 * Buttons send explicit broadcasts to this provider, which connects a short-lived MediaController to the
 * session and sends the command. Deviation from spec §8 (getForegroundService + ACTION_MEDIA_BUTTON): a
 * foreground-service start that does not lead to playback (e.g. "Next" while paused) must call startForeground
 * within seconds or the app crashes; a controller binding has no such deadline.
 */
class NowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = WidgetRenderer.build(context, WidgetStateStore.read(context))
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            ACTION_PREVIOUS, ACTION_TOGGLE, ACTION_NEXT -> sendCommand(context.applicationContext, action)
            else -> super.onReceive(context, intent)
        }
    }

    private fun sendCommand(context: Context, action: String) {
        val pending = goAsync()
        val done = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        val finish = Runnable {
            if (done.compareAndSet(false, true)) {
                MediaController.releaseFuture(future)
                pending.finish()
            }
        }
        handler.postDelayed(finish, TIMEOUT_MS) // never hold the broadcast longer than this

        future.addListener({
            try {
                val c = future.get()
                if (c.mediaItemCount == 0) {
                    // Stale "active" state (e.g. the process was killed): show the empty state again.
                    WidgetStateStore.write(context, WidgetState())
                    WidgetRenderer.updateAll(context, WidgetState())
                } else {
                    when (action) {
                        ACTION_PREVIOUS -> c.seekToPrevious()
                        ACTION_NEXT -> c.seekToNext()
                        ACTION_TOGGLE -> {
                            val showsPause = c.playWhenReady &&
                                c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE
                            if (showsPause) {
                                c.pause()
                            } else {
                                when (c.playbackState) {
                                    Player.STATE_IDLE -> c.prepare()
                                    Player.STATE_ENDED -> c.seekToDefaultPosition()
                                }
                                c.play()
                            }
                        }
                    }
                    Log.d(TAG, "widget command $action sent")
                }
            } catch (e: Exception) {
                Log.w(TAG, "widget command $action failed: ${e.message}")
            }
            // Give the session a moment to process the command before the controller disconnects.
            handler.postDelayed(finish, RELEASE_DELAY_MS)
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        const val ACTION_PREVIOUS = "dev.localmusic.widget.PREVIOUS"
        const val ACTION_TOGGLE = "dev.localmusic.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "dev.localmusic.widget.NEXT"
        private const val TAG = "NowPlayingWidget"
        private const val TIMEOUT_MS = 8_000L
        private const val RELEASE_DELAY_MS = 1_000L
    }
}
