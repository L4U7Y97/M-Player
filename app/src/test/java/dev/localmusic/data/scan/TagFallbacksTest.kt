package dev.localmusic.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagFallbacksTest {

    private fun resolve(raw: RawTags = RawTags(), fileName: String = "01 - Song.mp3", parent: String = "AlbumDir") =
        TagFallbacks.resolve(raw, fileName, parent)

    @Test
    fun allTagsPresent_usedTrimmed() {
        val r = resolve(
            RawTags(
                title = "  Title ", artist = " Artist ", albumArtist = " AA ", album = " Album ",
                trackNumber = "3/12", discNumber = "1/2", durationMs = "215000", year = "2003",
            ),
        )
        assertEquals(ResolvedTags("Title", "Artist", "Album", "AA", 3, 1, 215000L, 2003), r)
    }

    @Test
    fun noTags_allFallbacks() {
        val r = resolve(RawTags(), fileName = "01 - Song.mp3", parent = "Greatest Hits")
        assertEquals("01 - Song", r.title)
        assertEquals("Unknown artist", r.artist)
        assertEquals("Greatest Hits", r.album)
        assertNull(r.albumArtist)
        assertNull(r.trackNumber)
        assertNull(r.discNumber)
        assertEquals(0L, r.durationMs)
        assertNull(r.year)
    }

    @Test
    fun blankTitle_fallsBackToFileNameWithoutExtension() {
        assertEquals("Song", resolve(RawTags(title = "   "), fileName = "Song.flac").title)
        assertEquals("Song", resolve(RawTags(title = ""), fileName = "Song.flac").title)
    }

    @Test
    fun blankArtist_fallsBackToAlbumArtist() {
        val r = resolve(RawTags(artist = " ", albumArtist = "Band"))
        assertEquals("Band", r.artist)
        assertEquals("Band", r.albumArtist)
    }

    @Test
    fun noArtistNoAlbumArtist_unknownArtist() {
        assertEquals(TagFallbacks.UNKNOWN_ARTIST, resolve(RawTags(artist = "\t", albumArtist = " ")).artist)
        assertEquals("Unknown artist", TagFallbacks.UNKNOWN_ARTIST)
    }

    @Test
    fun blankAlbum_fallsBackToParentFolderName() {
        assertEquals("Rock", resolve(RawTags(album = "  "), parent = "Rock").album)
        assertEquals("Rock", resolve(RawTags(album = null), parent = "  Rock  ").album)
    }

    @Test
    fun blankAlbumAndBlankParent_unknownAlbum() {
        assertEquals(TagFallbacks.UNKNOWN_ALBUM, resolve(RawTags(), parent = " ").album)
    }

    @Test
    fun blankAlbumArtist_isNull() {
        assertNull(resolve(RawTags(artist = "A", albumArtist = "   ")).albumArtist)
    }

    @Test
    fun clean_trimsAndBlankToNull() {
        assertEquals("x", TagFallbacks.clean("  x \n"))
        assertNull(TagFallbacks.clean("   "))
        assertNull(TagFallbacks.clean(""))
        assertNull(TagFallbacks.clean(null))
    }

    @Test
    fun titleFromFileName_stripsOnlyLastExtension() {
        assertEquals("a.b", TagFallbacks.titleFromFileName("a.b.mp3"))
        assertEquals("01 - Song", TagFallbacks.titleFromFileName("01 - Song.mp3"))
    }

    @Test
    fun titleFromFileName_noDotOrDotOnly() {
        assertEquals("README", TagFallbacks.titleFromFileName("README"))
        assertEquals(".mp3", TagFallbacks.titleFromFileName(".mp3"))
    }

    @Test
    fun parseNumber_acceptsPlainSlashAndPadded() {
        assertEquals(3, TagFallbacks.parseNumber("3"))
        assertEquals(3, TagFallbacks.parseNumber("3/12"))
        assertEquals(3, TagFallbacks.parseNumber(" 03 "))
        assertEquals(12, TagFallbacks.parseNumber("12 / 14"))
    }

    @Test
    fun parseNumber_garbageZeroNegativeBlank_null() {
        for (v in listOf("abc", "", "  ", "0", "-1", "/12", "x/12", null)) {
            assertNull("value=$v", TagFallbacks.parseNumber(v))
        }
    }

    @Test
    fun parseYear_firstFourDigitRun() {
        assertEquals(2003, TagFallbacks.parseYear("2003"))
        assertEquals(2003, TagFallbacks.parseYear("2003-05-01"))
        assertEquals(1999, TagFallbacks.parseYear("Released 1999"))
    }

    @Test
    fun parseYear_noneOrZero_null() {
        assertNull(TagFallbacks.parseYear(null))
        assertNull(TagFallbacks.parseYear(""))
        assertNull(TagFallbacks.parseYear("abc"))
        assertNull(TagFallbacks.parseYear("99"))
        assertNull(TagFallbacks.parseYear("0000"))
    }

    @Test
    fun parseDuration_validAndInvalid() {
        assertEquals(215000L, TagFallbacks.parseDuration("215000"))
        assertEquals(215000L, TagFallbacks.parseDuration(" 215000 "))
        assertEquals(0L, TagFallbacks.parseDuration(null))
        assertEquals(0L, TagFallbacks.parseDuration("abc"))
        assertEquals(0L, TagFallbacks.parseDuration("-5"))
        assertEquals(0L, TagFallbacks.parseDuration(""))
    }

    @Test
    fun resolve_garbageNumbersBecomeNull() {
        val r = resolve(RawTags(trackNumber = "A1", discNumber = "zero", year = "n/a", durationMs = "long"))
        assertNull(r.trackNumber)
        assertNull(r.discNumber)
        assertNull(r.year)
        assertEquals(0L, r.durationMs)
    }

    @Test
    fun resolve_whitespaceOnlyTags_neverBlankResult() {
        val r = resolve(RawTags(title = " ", artist = " ", album = " "), fileName = "x.mp3", parent = "P")
        assertEquals("x", r.title)
        assertEquals("Unknown artist", r.artist)
        assertEquals("P", r.album)
    }
}
