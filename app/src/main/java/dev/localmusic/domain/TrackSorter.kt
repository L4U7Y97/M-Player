package dev.localmusic.domain

/**
 * Pure sorting of detail lists (spec §6). Stable (uses [sortedWith]).
 *
 * - TRACK_NUMBER: key (discNumber ?: 1, trackNumber). Tracks with null trackNumber are always last, in both
 *   directions; among themselves they are ordered by the fileName tie-break only.
 * - TITLE / FILE_NAME: natural, case-insensitive ([NaturalOrder]: "Track 2" before "Track 10").
 * - DATE_ADDED: numeric.
 * - Descending reverses only the primary key. Tie-break is always fileName ascending
 *   (natural case-insensitive, then case-insensitive, then exact String order), never reversed.
 */
object TrackSorter {

    /**
     * fileName ascending: natural case-insensitive first, then plain case-insensitive (so "01" vs "1" is
     * deterministic), then exact compare for determinism.
     */
    val FILE_NAME_TIE_BREAK: Comparator<SortableTrack> = Comparator { a, b ->
        val nat = NaturalOrder.compare(a.fileName, b.fileName)
        if (nat != 0) return@Comparator nat
        val ci = String.CASE_INSENSITIVE_ORDER.compare(a.fileName, b.fileName)
        if (ci != 0) ci else a.fileName.compareTo(b.fileName)
    }

    fun <T : SortableTrack> sort(tracks: List<T>, spec: SortSpec): List<T> =
        tracks.sortedWith(comparator(spec))

    fun comparator(spec: SortSpec): Comparator<SortableTrack> {
        val primary: Comparator<SortableTrack> = when (spec.field) {
            SortField.TRACK_NUMBER -> trackNumberComparator(spec.ascending)
            SortField.TITLE -> directed(spec.ascending) { a, b ->
                NaturalOrder.compare(a.title, b.title)
            }
            SortField.FILE_NAME -> directed(spec.ascending) { a, b ->
                NaturalOrder.compare(a.fileName, b.fileName)
            }
            SortField.DATE_ADDED -> directed(spec.ascending) { a, b -> a.dateAdded.compareTo(b.dateAdded) }
        }
        return Comparator { a, b ->
            val p = primary.compare(a, b)
            if (p != 0) p else FILE_NAME_TIE_BREAK.compare(a, b)
        }
    }

    private fun directed(ascending: Boolean, cmp: Comparator<SortableTrack>): Comparator<SortableTrack> =
        if (ascending) cmp else Comparator { a, b -> cmp.compare(b, a) }

    private fun trackNumberComparator(ascending: Boolean): Comparator<SortableTrack> = Comparator { a, b ->
        val ta = a.trackNumber
        val tb = b.trackNumber
        when {
            ta == null && tb == null -> 0
            ta == null -> 1 // nulls last regardless of direction
            tb == null -> -1
            else -> {
                val byDisc = (a.discNumber ?: 1).compareTo(b.discNumber ?: 1)
                val key = if (byDisc != 0) byDisc else ta.compareTo(tb)
                if (ascending) key else -key
            }
        }
    }
}
