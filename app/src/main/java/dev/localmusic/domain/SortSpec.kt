package dev.localmusic.domain

enum class SortField { TRACK_NUMBER, TITLE, FILE_NAME, DATE_ADDED }

/** One global sort order for detail lists (spec §6). Default: TRACK_NUMBER ascending. */
data class SortSpec(
    val field: SortField = SortField.TRACK_NUMBER,
    val ascending: Boolean = true,
) {
    companion object {
        val DEFAULT = SortSpec()
    }
}

/** Minimal view of a track that [TrackSorter] needs. [Track] implements it. */
interface SortableTrack {
    val title: String
    val fileName: String
    val trackNumber: Int?
    val discNumber: Int?
    val dateAdded: Long
}
