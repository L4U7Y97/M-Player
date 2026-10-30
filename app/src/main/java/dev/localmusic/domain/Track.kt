package dev.localmusic.domain

/** UI/domain model of a library track. Pure Kotlin; [uri] is the document URI as a String. */
data class Track(
    val id: String,
    val uri: String,
    val folderId: Long,
    val parentPath: String,
    val parentName: String,
    override val fileName: String,
    override val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    override val trackNumber: Int?,
    override val discNumber: Int?,
    val durationMs: Long,
    val year: Int?,
    override val dateAdded: Long,
    val missing: Boolean = false,
) : SortableTrack {
    /** Album grouping artist: albumArtist when present, else artist (matches the Albums query). */
    val albumGroupArtist: String get() = albumArtist ?: artist
}
