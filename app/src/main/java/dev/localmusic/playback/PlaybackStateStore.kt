package dev.localmusic.playback

import android.content.Context
import androidx.core.content.edit
import dev.localmusic.domain.PlaybackSnapshot
import dev.localmusic.domain.RepeatMode
import dev.localmusic.settings.SettingsMapper
import org.json.JSONArray

/**
 * SharedPreferences-backed [PlaybackSnapshot], written by PlaybackService and read back when it starts. The queue is
 * stored separately from the cheap fields (index, position, modes) so frequent position saves don't rewrite it.
 */
class PlaybackStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): PlaybackSnapshot? {
        val ids = prefs.getString(KEY_QUEUE, null)?.let(::parseStrings).orEmpty()
        if (ids.isEmpty()) return null
        return PlaybackSnapshot(
            trackIds = ids,
            currentIndex = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            shuffleOrder = prefs.getString(KEY_SHUFFLE_ORDER, null)?.let(::parseInts).orEmpty(),
            repeatMode = SettingsMapper.enumFromString(prefs.getString(KEY_REPEAT, null), RepeatMode.OFF),
        )
    }

    fun writeQueue(trackIds: List<String>, shuffleOrder: List<Int>) = prefs.edit {
        putString(KEY_QUEUE, JSONArray(trackIds).toString())
        putString(KEY_SHUFFLE_ORDER, JSONArray(shuffleOrder).toString())
    }

    fun writePosition(currentIndex: Int, positionMs: Long, shuffle: Boolean, repeatMode: RepeatMode) = prefs.edit {
        putInt(KEY_INDEX, currentIndex)
        putLong(KEY_POSITION, positionMs)
        putBoolean(KEY_SHUFFLE, shuffle)
        putString(KEY_REPEAT, SettingsMapper.enumToString(repeatMode))
    }

    private fun parseStrings(json: String): List<String> = runCatching {
        val a = JSONArray(json)
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun parseInts(json: String): List<Int> = runCatching {
        val a = JSONArray(json)
        List(a.length()) { a.getInt(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS = "playback_state"
        const val KEY_QUEUE = "queue"
        const val KEY_SHUFFLE_ORDER = "shuffle_order"
        const val KEY_INDEX = "current_index"
        const val KEY_POSITION = "position_ms"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat_mode"
    }
}
