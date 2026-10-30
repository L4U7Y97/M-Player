package dev.localmusic.ui.nav

import android.net.Uri

/** String routes (spec §10). Path args are Uri-encoded; Navigation decodes them when reading arguments. */
object Routes {
    const val LIBRARY = "library"
    const val FOLDER = "folder/{path}"
    const val ARTIST = "artist/{name}"
    const val ALBUM = "album/{album}/{artist}"
    const val PLAYLIST = "playlist/{id}"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val SETTINGS_FOLDERS = "settings/folders"
    const val SETTINGS_EQUALIZER = "settings/equalizer"
    const val SETTINGS_THEME = "settings/theme"

    private fun enc(value: String): String = Uri.encode(value.ifEmpty { " " })

    fun folder(path: String) = "folder/${enc(path)}"
    fun artist(name: String) = "artist/${enc(name)}"
    fun album(album: String, artist: String) = "album/${enc(album)}/${enc(artist)}"
    fun playlist(id: Long) = "playlist/$id"
}
