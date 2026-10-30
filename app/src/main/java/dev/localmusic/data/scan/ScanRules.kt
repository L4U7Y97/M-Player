package dev.localmusic.data.scan

/**
 * Pure rules used by [FolderScanner] while walking a picked folder tree (spec §4).
 * No android.* imports: unit-tested on the JVM.
 */
object ScanRules {

    /** Lower-case audio extensions the scanner accepts (compared case-insensitively). */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav")

    /** True for names starting with '.', e.g. `.nomedia`, `.hidden.mp3`, `.thumbnails`. */
    fun isHidden(name: String): Boolean = name.startsWith(".")

    /**
     * Lower-cased extension after the last '.', or null when there is none
     * (no dot, or the dot is the last character).
     */
    fun extensionOf(name: String): String? {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return null
        return name.substring(dot + 1).lowercase()
    }

    /** True when [name] is a visible file with a supported audio extension (`song.MP3` → true). */
    fun isAudioFile(name: String): Boolean {
        if (name.isBlank() || isHidden(name)) return false
        val ext = extensionOf(name) ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    /**
     * Whether the contents of a directory at [depth] should be listed.
     * depth 0 = the picked root: always listed. depth >= 1 (subfolders): only when [includeSubfolders].
     * Negative depth is treated like the root.
     */
    fun shouldDescend(depth: Int, includeSubfolders: Boolean): Boolean =
        depth <= 0 || includeSubfolders

    /** Marker file that hides a folder (and everything below it) from media scanning, like Android's MediaStore. */
    const val NO_MEDIA_FILE = ".nomedia"

    /** True when a directory listing [childNames] contains a `.nomedia` file (name compared case-insensitively). */
    fun hasNoMediaMarker(childNames: Collection<String>): Boolean =
        childNames.any { it.trim().equals(NO_MEDIA_FILE, ignoreCase = true) }

    /**
     * Whether a directory at [depth] whose listing is [childNames] must be skipped entirely (its files and its
     * subfolders): any **subfolder** (depth >= 1) containing `.nomedia`. The picked root itself (depth <= 0) is
     * never skipped, because the user chose it explicitly.
     */
    fun shouldSkipDirectory(depth: Int, childNames: Collection<String>): Boolean =
        depth >= 1 && hasNoMediaMarker(childNames)

    /** Whether a child directory named [name] may be entered at all (hidden directories are skipped). */
    fun shouldVisitDirectory(name: String): Boolean = name.isNotBlank() && !isHidden(name)

    /**
     * Display path of a child: `childPath("Music/Rock", "AlbumX")` → `"Music/Rock/AlbumX"`.
     * Trailing '/' on the parent is dropped; an empty/blank parent yields just [name].
     */
    fun childPath(parentPath: String, name: String): String {
        val parent = parentPath.trimEnd('/')
        return if (parent.isBlank()) name else "$parent/$name"
    }
}
