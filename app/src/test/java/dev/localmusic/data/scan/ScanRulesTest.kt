package dev.localmusic.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRulesTest {

    @Test
    fun supportedExtensions_matchSpecList() {
        assertEquals(
            setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav"),
            ScanRules.SUPPORTED_EXTENSIONS,
        )
    }

    @Test
    fun isAudioFile_acceptsEverySupportedExtension() {
        for (ext in listOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav")) {
            assertTrue("song.$ext", ScanRules.isAudioFile("song.$ext"))
        }
    }

    @Test
    fun isAudioFile_extensionIsCaseInsensitive() {
        assertTrue(ScanRules.isAudioFile("Song.MP3"))
        assertTrue(ScanRules.isAudioFile("Song.Flac"))
        assertTrue(ScanRules.isAudioFile("SONG.OPUS"))
    }

    @Test
    fun isAudioFile_rejectsUnsupportedExtensions() {
        for (name in listOf("cover.jpg", "notes.txt", "playlist.m3u", "video.mp4", "track.wma", "lyrics.lrc", "a.mp3.bak")) {
            assertFalse(name, ScanRules.isAudioFile(name))
        }
    }

    @Test
    fun isAudioFile_rejectsHiddenFiles() {
        assertFalse(ScanRules.isAudioFile(".hidden.mp3"))
        assertFalse(ScanRules.isAudioFile(".mp3"))
        assertFalse(ScanRules.isAudioFile(".nomedia"))
    }

    @Test
    fun isAudioFile_rejectsNoExtensionTrailingDotAndBlank() {
        assertFalse(ScanRules.isAudioFile("mp3"))
        assertFalse(ScanRules.isAudioFile("song."))
        assertFalse(ScanRules.isAudioFile(""))
        assertFalse(ScanRules.isAudioFile("   "))
    }

    @Test
    fun isAudioFile_usesLastExtensionOnly() {
        assertTrue(ScanRules.isAudioFile("my.song.v2.mp3"))
        assertFalse(ScanRules.isAudioFile("song.mp3.txt"))
    }

    @Test
    fun isHidden_dotPrefixOnly() {
        assertTrue(ScanRules.isHidden(".thumbnails"))
        assertTrue(ScanRules.isHidden(".x.mp3"))
        assertFalse(ScanRules.isHidden("x.mp3"))
        assertFalse(ScanRules.isHidden("a.b"))
    }

    @Test
    fun extensionOf_lowerCasesLastExtension() {
        assertEquals("mp3", ScanRules.extensionOf("A.MP3"))
        assertEquals("flac", ScanRules.extensionOf("a.b.flac"))
    }

    @Test
    fun extensionOf_nullWithoutExtension() {
        assertNull(ScanRules.extensionOf("README"))
        assertNull(ScanRules.extensionOf("song."))
        assertNull(ScanRules.extensionOf(""))
    }

    @Test
    fun shouldDescend_rootAlwaysListed() {
        assertTrue(ScanRules.shouldDescend(0, includeSubfolders = true))
        assertTrue(ScanRules.shouldDescend(0, includeSubfolders = false))
    }

    @Test
    fun shouldDescend_subfoldersOnlyWhenIncluded() {
        assertTrue(ScanRules.shouldDescend(1, includeSubfolders = true))
        assertTrue(ScanRules.shouldDescend(5, includeSubfolders = true))
        assertFalse(ScanRules.shouldDescend(1, includeSubfolders = false))
        assertFalse(ScanRules.shouldDescend(3, includeSubfolders = false))
    }

    @Test
    fun shouldDescend_negativeDepthTreatedAsRoot() {
        assertTrue(ScanRules.shouldDescend(-1, includeSubfolders = false))
    }

    @Test
    fun shouldVisitDirectory_skipsHiddenAndBlank() {
        assertTrue(ScanRules.shouldVisitDirectory("Rock"))
        assertTrue(ScanRules.shouldVisitDirectory("Album.2003"))
        assertFalse(ScanRules.shouldVisitDirectory(".thumbnails"))
        assertFalse(ScanRules.shouldVisitDirectory(".git"))
        assertFalse(ScanRules.shouldVisitDirectory(""))
        assertFalse(ScanRules.shouldVisitDirectory("  "))
    }

    @Test
    fun childPath_joinsWithSlash() {
        assertEquals("Music/Rock/AlbumX", ScanRules.childPath("Music/Rock", "AlbumX"))
        assertEquals("Music/AlbumX", ScanRules.childPath("Music", "AlbumX"))
    }

    @Test
    fun childPath_dropsTrailingSlash() {
        assertEquals("Music/Rock/X", ScanRules.childPath("Music/Rock/", "X"))
    }

    @Test
    fun childPath_blankParentYieldsName() {
        assertEquals("Music", ScanRules.childPath("", "Music"))
        assertEquals("Music", ScanRules.childPath("/", "Music"))
    }

    /** Simulates the scanner's BFS with the pure rules over an in-memory tree (spec §4, T7 AC). */
    @Test
    fun simulatedWalk_includeSubfoldersToggle() {
        val tree = mapOf(
            "Music" to listOf("a.mp3", "cover.jpg", ".hidden.mp3", "Sub/", ".cache/"),
            "Music/Sub" to listOf("b.flac", "Deep/"),
            "Music/Sub/Deep" to listOf("c.ogg"),
            "Music/.cache" to listOf("d.mp3"),
        )

        fun walk(include: Boolean): List<String> {
            val found = mutableListOf<String>()
            val queue = ArrayDeque(listOf("Music" to 0))
            while (queue.isNotEmpty()) {
                val (path, depth) = queue.removeFirst()
                if (!ScanRules.shouldDescend(depth, include)) continue
                for (entry in tree[path].orEmpty()) {
                    if (entry.endsWith("/")) {
                        val name = entry.dropLast(1)
                        if (ScanRules.shouldVisitDirectory(name)) queue.add(ScanRules.childPath(path, name) to depth + 1)
                    } else if (ScanRules.isAudioFile(entry)) {
                        found += ScanRules.childPath(path, entry)
                    }
                }
            }
            return found
        }
        assertEquals(listOf("Music/a.mp3", "Music/Sub/b.flac", "Music/Sub/Deep/c.ogg"), walk(true))
        assertEquals(listOf("Music/a.mp3"), walk(false))
    }

    // ---- round 3: .nomedia ----

    @Test
    fun noMediaMarker_detectedCaseInsensitively() {
        assertTrue(ScanRules.hasNoMediaMarker(listOf("a.mp3", ".nomedia")))
        assertTrue(ScanRules.hasNoMediaMarker(listOf(".NoMedia")))
        assertFalse(ScanRules.hasNoMediaMarker(listOf("a.mp3", "nomedia", ".nomedia.txt")))
        assertFalse(ScanRules.hasNoMediaMarker(emptyList()))
    }

    @Test
    fun shouldSkipDirectory_subfolderWithNoMediaOnly() {
        assertTrue(ScanRules.shouldSkipDirectory(1, listOf(".nomedia", "a.mp3")))
        assertTrue(ScanRules.shouldSkipDirectory(3, listOf(".nomedia")))
        assertFalse(ScanRules.shouldSkipDirectory(1, listOf("a.mp3")))
        // the picked root is never skipped
        assertFalse(ScanRules.shouldSkipDirectory(0, listOf(".nomedia", "a.mp3")))
    }

    @Test
    fun simulatedWalk_skipsNoMediaSubtree() {
        val tree = mapOf(
            "Music" to listOf("a.mp3", "Hidden/", "Keep/"),
            "Music/Hidden" to listOf(".nomedia", "b.mp3", "Deeper/"),
            "Music/Hidden/Deeper" to listOf("c.mp3"),
            "Music/Keep" to listOf("d.mp3"),
        )
        val found = mutableListOf<String>()
        val queue = ArrayDeque(listOf("Music" to 0))
        while (queue.isNotEmpty()) {
            val (path, depth) = queue.removeFirst()
            val children = tree[path].orEmpty()
            if (ScanRules.shouldSkipDirectory(depth, children)) continue
            for (entry in children) {
                if (entry.endsWith("/")) {
                    val name = entry.dropLast(1)
                    if (ScanRules.shouldVisitDirectory(name)) queue.add(ScanRules.childPath(path, name) to depth + 1)
                } else if (ScanRules.isAudioFile(entry)) {
                    found += ScanRules.childPath(path, entry)
                }
            }
        }
        assertEquals(listOf("Music/a.mp3", "Music/Keep/d.mp3"), found)
    }
}
