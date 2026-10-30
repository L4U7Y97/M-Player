package dev.localmusic.domain

/**
 * Pure view of a playlist entry. [entryId] = 0 for entries not yet stored (Room auto-generates).
 * Duplicates of the same [trackId] are allowed.
 */
data class PlaylistItem(
    val entryId: Long = 0,
    val trackId: String,
    val position: Int,
)

/**
 * Pure playlist reorder logic (spec §6). Every operation works on the **list order** of its input and returns a
 * new list whose positions are renumbered 0..n-1, including no-ops (out-of-range indices), which return the
 * input order renumbered.
 */
object PlaylistOrdering {

    /** Positions set to list index. */
    fun renumber(entries: List<PlaylistItem>): List<PlaylistItem> =
        entries.mapIndexed { i, e -> if (e.position == i) e else e.copy(position = i) }

    /** Sorts by stored position (stable for equal positions), then renumbers. */
    fun normalize(entries: List<PlaylistItem>): List<PlaylistItem> =
        renumber(entries.sortedBy { it.position })

    /** Item at [from] ends up at index [to]. Either index out of range -> no-op. */
    fun move(entries: List<PlaylistItem>, from: Int, to: Int): List<PlaylistItem> {
        if (from !in entries.indices || to !in entries.indices || from == to) return renumber(entries)
        val list = entries.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return renumber(list)
    }

    /** Removes the entry at [index]. Out of range -> no-op. */
    fun remove(entries: List<PlaylistItem>, index: Int): List<PlaylistItem> {
        if (index !in entries.indices) return renumber(entries)
        val list = entries.toMutableList()
        list.removeAt(index)
        return renumber(list)
    }

    /** Appends new entries (entryId 0) for [trackIds] in order; duplicates allowed. */
    fun append(entries: List<PlaylistItem>, trackIds: List<String>): List<PlaylistItem> =
        renumber(entries + trackIds.map { PlaylistItem(entryId = 0, trackId = it, position = 0) })
}
