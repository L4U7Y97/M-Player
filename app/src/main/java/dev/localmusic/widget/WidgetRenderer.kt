package dev.localmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.localmusic.MainActivity
import dev.localmusic.R

/** Builds the RemoteViews for a [WidgetState] and pushes them to every placed widget (spec §8). */
object WidgetRenderer {

    fun updateAll(context: Context, state: WidgetState = WidgetStateStore.read(context)) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidgetProvider::class.java))
        if (ids == null || ids.isEmpty()) return
        manager.updateAppWidget(ids, build(context, state))
    }

    fun build(context: Context, state: WidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
        val openApp = openAppIntent(context)
        views.setOnClickPendingIntent(R.id.widget_root, openApp)
        views.setOnClickPendingIntent(R.id.widget_text, openApp)

        if (state.active) {
            views.setTextViewText(R.id.widget_title, state.title.ifBlank { context.getString(R.string.app_name) })
            views.setTextViewText(R.id.widget_artist, state.artist)
            views.setOnClickPendingIntent(R.id.widget_previous, command(context, NowPlayingWidgetProvider.ACTION_PREVIOUS, 11))
            views.setOnClickPendingIntent(R.id.widget_play_pause, command(context, NowPlayingWidgetProvider.ACTION_TOGGLE, 12))
            views.setOnClickPendingIntent(R.id.widget_next, command(context, NowPlayingWidgetProvider.ACTION_NEXT, 13))
        } else {
            // Nothing loaded / service not running: every button just opens the app (spec §8).
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_not_playing))
            views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_open_app_hint))
            views.setOnClickPendingIntent(R.id.widget_previous, openApp)
            views.setOnClickPendingIntent(R.id.widget_play_pause, openApp)
            views.setOnClickPendingIntent(R.id.widget_next, openApp)
        }
        val playing = state.active && state.isPlaying
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (playing) R.string.widget_pause else R.string.widget_play),
        )
        return views
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun command(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NowPlayingWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private const val REQUEST_OPEN_APP = 10
}
