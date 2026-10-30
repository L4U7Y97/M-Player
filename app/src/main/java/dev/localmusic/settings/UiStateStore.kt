package dev.localmusic.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Browsing state kept between launches: the selected Library tab and the last opened track list (folder, artist,
 * album or playlist route). SharedPreferences, read synchronously on the first frame.
 */
class UiStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var libraryTab: Int
        get() = prefs.getInt(KEY_TAB, 0)
        set(value) = prefs.edit { putInt(KEY_TAB, value) }

    /** Concrete route (e.g. `folder/Music%2FRock`), or null when the user was on the Library screen. */
    var lastListRoute: String?
        get() = prefs.getString(KEY_ROUTE, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_ROUTE) else putString(KEY_ROUTE, value) }

    private companion object {
        const val PREFS = "ui_state"
        const val KEY_TAB = "library_tab"
        const val KEY_ROUTE = "last_list_route"
    }
}
