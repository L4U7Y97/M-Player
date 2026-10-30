package dev.localmusic.widget

import android.content.Context

/** What the widget shows (spec §8). [active] = the playback service is running with a non-empty queue. */
data class WidgetState(
    val active: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
)

/** SharedPreferences-backed widget state, written by PlaybackService and read by the provider. */
object WidgetStateStore {
    private const val PREFS = "widget_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "is_playing"

    fun read(context: Context): WidgetState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetState(
            active = p.getBoolean(KEY_ACTIVE, false),
            title = p.getString(KEY_TITLE, "").orEmpty(),
            artist = p.getString(KEY_ARTIST, "").orEmpty(),
            isPlaying = p.getBoolean(KEY_PLAYING, false),
        )
    }

    fun write(context: Context, state: WidgetState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .putString(KEY_TITLE, state.title)
            .putString(KEY_ARTIST, state.artist)
            .putBoolean(KEY_PLAYING, state.isPlaying)
            .apply()
    }
}
