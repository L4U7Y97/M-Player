package dev.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSorterTest {

    private data class T(
        override val fileName: String,
        override val title: String = fileName,
        override val trackNumber: Int? = null,
        override val discNumber: Int? = null,
        override val dateAdded: Long = 0L,
    ) : SortableTrack

    private fun names(list: List<SortableTrack>) = list.map { it.fileName }

    private fun sort(list: List<T>, field: SortField, asc: Boolean = true) =
        names(TrackSorter.sort(list, SortSpec(field, asc)))

    @Test
    fun defaultSpec_isTrackNumberAscending() {
        assertEquals(SortSpec(SortField.TRACK_NUMBER, true), SortSpec.DEFAULT)
        assertEquals(SortSpec.DEFAULT, SortSpec())
    }

    @Test
    fun trackNumber_ascending() {
        val list = listOf(T("c", trackNumber = 3), T("a", trackNumber = 1), T("b", trackNumber = 2))
        assertEquals(listOf("a", "b", "c"), sort(list, SortField.TRACK_NUMBER))
    }

    @Test
    fun trackNumber_descending() {
        val list = listOf(T("c", trackNumber = 3), T("a", trackNumber = 1), T("b", trackNumber = 2))
        assertEquals(listOf("c", "b", "a"), sort(list, SortField.TRACK_NUMBER, asc = false))
    }

    @Test
    fun trackNumber_numericNotLexical() {
        val list = listOf(T("x10", trackNumber = 10), T("x2", trackNumber = 2), T("x1", trackNumber = 1))
        assertEquals(listOf("x1", "x2", "x10"), sort(list, SortField.TRACK_NUMBER))
    }

    @Test
    fun trackNumber_missingNumbersLast_ascending() {
        val list = listOf(T("n1"), T("t2", trackNumber = 2), T("n0"), T("t1", trackNumber = 1))
        assertEquals(listOf("t1", "t2", "n0", "n1"), sort(list, SortField.TRACK_NUMBER))
    }

    @Test
    fun trackNumber_missingNumbersLast_descending() {
        val list = listOf(T("n1"), T("t2", trackNumber = 2), T("n0"), T("t1", trackNumber = 1))
        // nulls stay last and among themselves keep fileName ascending
        assertEquals(listOf("t2", "t1", "n0", "n1"), sort(list, SortField.TRACK_NUMBER, asc = false))
    }

    @Test
    fun trackNumber_allMissing_orderedByFileName() {
        val list = listOf(T("b"), T("C"), T("a"))
        assertEquals(listOf("a", "b", "C"), sort(list, SortField.TRACK_NUMBER))
        assertEquals(listOf("a", "b", "C"), sort(list, SortField.TRACK_NUMBER, asc = false))
    }

    @Test
    fun trackNumber_discBeforeTrack_nullDiscCountsAsOne() {
        val list = listOf(
            T("d2t1", trackNumber = 1, discNumber = 2),
            T("d1t10", trackNumber = 10, discNumber = 1),
            T("nodisc_t5", trackNumber = 5, discNumber = null),
            T("d1t1", trackNumber = 1, discNumber = 1),
        )
        assertEquals(listOf("d1t1", "nodisc_t5", "d1t10", "d2t1"), sort(list, SortField.TRACK_NUMBER))
        assertEquals(listOf("d2t1", "d1t10", "nodisc_t5", "d1t1"), sort(list, SortField.TRACK_NUMBER, asc = false))
    }

    @Test
    fun trackNumber_tiesBrokenByFileNameAscendingInBothDirections() {
        val list = listOf(T("b", trackNumber = 1), T("a", trackNumber = 1), T("z", trackNumber = 2))
        assertEquals(listOf("a", "b", "z"), sort(list, SortField.TRACK_NUMBER))
        assertEquals(listOf("z", "a", "b"), sort(list, SortField.TRACK_NUMBER, asc = false))
    }

    @Test
    fun title_ascendingIsCaseInsensitive() {
        val list = listOf(T("1", title = "banana"), T("2", title = "Apple"), T("3", title = "cherry"), T("4", title = "apricot"))
        assertEquals(listOf("2", "4", "1", "3"), sort(list, SortField.TITLE))
    }

    @Test
    fun title_descendingIsCaseInsensitive() {
        val list = listOf(T("1", title = "banana"), T("2", title = "Apple"), T("3", title = "Cherry"))
        assertEquals(listOf("3", "1", "2"), sort(list, SortField.TITLE, asc = false))
    }

    @Test
    fun title_sameTitleDifferentCase_tieBreakFileNameAscending() {
        val list = listOf(T("b.mp3", title = "Song"), T("a.mp3", title = "SONG"), T("c.mp3", title = "song"))
        assertEquals(listOf("a.mp3", "b.mp3", "c.mp3"), sort(list, SortField.TITLE))
        assertEquals(listOf("a.mp3", "b.mp3", "c.mp3"), sort(list, SortField.TITLE, asc = false))
    }

    @Test
    fun fileName_ascendingAndDescendingCaseInsensitive() {
        val list = listOf(T("beta.mp3"), T("Alpha.mp3"), T("gamma.mp3"), T("Delta.mp3"))
        assertEquals(listOf("Alpha.mp3", "beta.mp3", "Delta.mp3", "gamma.mp3"), sort(list, SortField.FILE_NAME))
        assertEquals(listOf("gamma.mp3", "Delta.mp3", "beta.mp3", "Alpha.mp3"), sort(list, SortField.FILE_NAME, asc = false))
    }

    @Test
    fun fileName_caseOnlyDifference_isDeterministic() {
        val a = listOf(T("song.mp3"), T("Song.mp3"))
        val b = listOf(T("Song.mp3"), T("song.mp3"))
        assertEquals(sort(a, SortField.FILE_NAME), sort(b, SortField.FILE_NAME))
    }

    @Test
    fun dateAdded_ascendingAndDescending() {
        val list = listOf(T("b", dateAdded = 200), T("a", dateAdded = 100), T("c", dateAdded = 300))
        assertEquals(listOf("a", "b", "c"), sort(list, SortField.DATE_ADDED))
        assertEquals(listOf("c", "b", "a"), sort(list, SortField.DATE_ADDED, asc = false))
    }

    @Test
    fun dateAdded_tiesBrokenByFileNameAscendingInBothDirections() {
        val list = listOf(T("y", dateAdded = 5), T("x", dateAdded = 5), T("old", dateAdded = 1))
        assertEquals(listOf("old", "x", "y"), sort(list, SortField.DATE_ADDED))
        assertEquals(listOf("x", "y", "old"), sort(list, SortField.DATE_ADDED, asc = false))
    }

    @Test
    fun dateAdded_largeValuesNoOverflow() {
        val list = listOf(T("max", dateAdded = Long.MAX_VALUE), T("min", dateAdded = Long.MIN_VALUE), T("zero"))
        assertEquals(listOf("min", "zero", "max"), sort(list, SortField.DATE_ADDED))
    }

    @Test
    fun sort_isStableForFullyEqualElements() {
        val first = T("same.mp3", trackNumber = 1)
        val second = T("same.mp3", trackNumber = 1)
        val sorted = TrackSorter.sort(listOf(first, T("a.mp3", trackNumber = 1), second), SortSpec.DEFAULT)
        assertTrue(sorted[1] === first)
        assertTrue(sorted[2] === second)
    }

    @Test
    fun sort_emptyAndSingleAndInputUnchanged() {
        assertEquals(emptyList<T>(), TrackSorter.sort(emptyList<T>(), SortSpec.DEFAULT))
        val single = listOf(T("x"))
        assertEquals(single, TrackSorter.sort(single, SortSpec(SortField.TITLE, false)))
        val input = listOf(T("b", trackNumber = 2), T("a", trackNumber = 1))
        TrackSorter.sort(input, SortSpec.DEFAULT)
        assertEquals(listOf("b", "a"), names(input))
    }

    @Test
    fun sort_worksOnTrackModel() {
        fun track(id: String, file: String, no: Int?) = Track(
            id = id, uri = "content://x/$id", folderId = 1, parentPath = "Music/A", parentName = "A",
            fileName = file, title = file, artist = "Ar", album = "Al", albumArtist = null,
            trackNumber = no, discNumber = null, durationMs = 0, year = null, dateAdded = 0,
        )
        val sorted = TrackSorter.sort(listOf(track("1", "z.mp3", 2), track("2", "y.mp3", null), track("3", "x.mp3", 1)), SortSpec.DEFAULT)
        assertEquals(listOf("3", "1", "2"), sorted.map { it.id })
    }

    @Test
    fun comparator_matchesSort() {
        val list = listOf(T("b", trackNumber = 2), T("a"), T("c", trackNumber = 1))
        for (field in SortField.values()) for (asc in listOf(true, false)) {
            val spec = SortSpec(field, asc)
            assertEquals("$spec", TrackSorter.sort(list, spec), list.sortedWith(TrackSorter.comparator(spec)))
        }
    }

    @Test
    fun fileNameTieBreak_caseInsensitiveFirst() {
        val cmp = TrackSorter.FILE_NAME_TIE_BREAK
        assertTrue(cmp.compare(T("apple"), T("Banana")) < 0)
        assertTrue(cmp.compare(T("Banana"), T("apple")) > 0)
        assertEquals(0, cmp.compare(T("same"), T("same")))
    }

    @Test
    fun track_albumGroupArtist_prefersAlbumArtist() {
        fun track(aa: String?) = Track(
            id = "1", uri = "u", folderId = 1, parentPath = "p", parentName = "p", fileName = "f", title = "t",
            artist = "Artist", album = "Al", albumArtist = aa, trackNumber = null, discNumber = null,
            durationMs = 0, year = null, dateAdded = 0,
        )
        assertEquals("Band", track("Band").albumGroupArtist)
        assertEquals("Artist", track(null).albumGroupArtist)
    }

    // ---- round 3: natural order ("Track 2" before "Track 10"), still case-insensitive ----

    @Test
    fun title_naturalNumberOrder() {
        val list = listOf(T("a", title = "Track 10"), T("b", title = "track 2"), T("c", title = "Track 1"))
        assertEquals(listOf("c", "b", "a"), sort(list, SortField.TITLE))
        assertEquals(listOf("a", "b", "c"), sort(list, SortField.TITLE, asc = false))
    }

    @Test
    fun fileName_naturalNumberOrder() {
        val list = listOf(T("Track 10.mp3"), T("track 2.mp3"), T("Track 1.mp3"), T("Track 9.mp3"))
        assertEquals(listOf("Track 1.mp3", "track 2.mp3", "Track 9.mp3", "Track 10.mp3"), sort(list, SortField.FILE_NAME))
        assertEquals(listOf("Track 10.mp3", "Track 9.mp3", "track 2.mp3", "Track 1.mp3"), sort(list, SortField.FILE_NAME, asc = false))
    }

    @Test
    fun fileName_zeroPaddingIsDeterministic() {
        val a = listOf(T("01.mp3"), T("1.mp3"), T("2.mp3"))
        val b = listOf(T("2.mp3"), T("1.mp3"), T("01.mp3"))
        assertEquals(sort(a, SortField.FILE_NAME), sort(b, SortField.FILE_NAME))
        assertEquals("2.mp3", sort(a, SortField.FILE_NAME).last())
    }

    @Test
    fun tieBreak_isNaturalToo() {
        val list = listOf(T("x10", trackNumber = 1), T("x9", trackNumber = 1))
        assertEquals(listOf("x9", "x10"), sort(list, SortField.TRACK_NUMBER))
    }

    @Test
    fun naturalOrder_basics() {
        assertTrue(NaturalOrder.compare("Track 2", "Track 10") < 0)
        assertTrue(NaturalOrder.compare("a", "B") < 0)
        assertEquals(0, NaturalOrder.compare("ABC 7", "abc 07"))
        assertTrue(NaturalOrder.compare("abc", "abc1") < 0)
        assertTrue(NaturalOrder.compare("99999999999999999999", "100000000000000000000") < 0)
        assertEquals(0, NaturalOrder.compare("", ""))
        assertTrue(NaturalOrder.compare("", "a") < 0)
    }
}
