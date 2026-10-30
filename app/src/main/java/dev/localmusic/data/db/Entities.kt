package dev.localmusic.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.localmusic.domain.Track

@Entity(
    tableName = "folders",
    indices = [Index(value = ["treeUri"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val includeSubfolders: Boolean = true,
    val addedAt: Long,
)

/**
 * id = "<authority>|<documentId>"; uri = document URI built from the folder's tree URI (what ExoPlayer plays).
 * parentPath = display path relative to the picked root, root name included (e.g. "Music/Rock/AlbumX").
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index("folderId"),
        Index("artist"),
        Index("album"),
        Index("parentPath"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val folderId: Long,
    val parentPath: String,
    val parentName: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val year: Int?,
    val dateAdded: Long,
    val size: Long,
    val lastModified: Long,
    val missing: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** No FK on trackId: entries survive track removal and show as missing. */
@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["playlistId", "position"])],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val position: Int,
)

// ---- grouping projections ----------------------------------------------------------------------------

data class FolderGroup(val path: String, val name: String, val trackCount: Int)

data class ArtistGroup(val name: String, val trackCount: Int)

/** artist = coalesce(albumArtist, artist). */
data class AlbumGroup(val album: String, val artist: String, val trackCount: Int)

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
)

/** A playlist entry with its track (null if the track row no longer exists). */
data class PlaylistEntryWithTrack(
    @Embedded val entry: PlaylistEntryEntity,
    @Relation(parentColumn = "trackId", entityColumn = "id")
    val track: TrackEntity?,
)

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    uri = uri,
    folderId = folderId,
    parentPath = parentPath,
    parentName = parentName,
    fileName = fileName,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    trackNumber = trackNumber,
    discNumber = discNumber,
    durationMs = durationMs,
    year = year,
    dateAdded = dateAdded,
    missing = missing,
)
