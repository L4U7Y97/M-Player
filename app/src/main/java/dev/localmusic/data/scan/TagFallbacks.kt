package dev.localmusic.data.scan

/** Raw strings as read from MediaMetadataRetriever; any may be null. */
data class RawTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val durationMs: String? = null,
    val year: String? = null,
)

/** Tags after fallbacks: title/artist/album are never blank. */
data class ResolvedTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val year: Int?,
)

/**
 * Pure tag fallback rules (spec §4). No android.* imports.
 */
object TagFallbacks {

    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"

    private val YEAR_REGEX = Regex("""\d{4}""")

    fun resolve(raw: RawTags, fileName: String, parentName: String): ResolvedTags {
        val albumArtist = clean(raw.albumArtist)
        return ResolvedTags(
            title = clean(raw.title) ?: titleFromFileName(fileName),
            artist = clean(raw.artist) ?: albumArtist ?: UNKNOWN_ARTIST,
            album = clean(raw.album) ?: clean(parentName) ?: UNKNOWN_ALBUM,
            albumArtist = albumArtist,
            trackNumber = parseNumber(raw.trackNumber),
            discNumber = parseNumber(raw.discNumber),
            durationMs = parseDuration(raw.durationMs),
            year = parseYear(raw.year),
        )
    }

    /** Trims; blank or whitespace-only → null. */
    fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * File name without its last extension (`"01 - Song.mp3"` → `"01 - Song"`).
     * No dot → whole name; if stripping leaves nothing (`".mp3"`) or the name is blank → the trimmed file name.
     */
    fun titleFromFileName(fileName: String): String {
        val trimmed = fileName.trim()
        val dot = trimmed.lastIndexOf('.')
        val base = if (dot > 0) trimmed.substring(0, dot).trim() else trimmed
        return base.ifEmpty { trimmed }
    }

    /**
     * Track/disc number: `"3"`, `"3/12"`, `" 03 "` → 3. Part before '/' is trimmed and parsed.
     * Garbage, blank, zero or negative → null.
     */
    fun parseNumber(value: String?): Int? {
        val head = clean(value)?.substringBefore('/')?.trim() ?: return null
        val n = head.toIntOrNull() ?: return null
        return n.takeIf { it > 0 }
    }

    /** First run of 4 digits anywhere in the string (`"2003-05-01"` → 2003); none or `"0000"` → null. */
    fun parseYear(value: String?): Int? {
        val s = clean(value) ?: return null
        val y = YEAR_REGEX.find(s)?.value?.toIntOrNull() ?: return null
        return y.takeIf { it > 0 }
    }

    /** Milliseconds as a decimal string; null/garbage/negative → 0. */
    fun parseDuration(value: String?): Long {
        val d = clean(value)?.toLongOrNull() ?: return 0L
        return if (d < 0) 0L else d
    }
}
