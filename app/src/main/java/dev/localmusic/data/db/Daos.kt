package dev.localmusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY displayName COLLATE NOCASE, id")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY id")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE treeUri = :treeUri")
    suspend fun getByTreeUri(treeUri: String): FolderEntity?

    /** Returns the new row id, or -1 if the treeUri already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET includeSubfolders = :include WHERE id = :id")
    suspend fun setIncludeSubfolders(id: Long, include: Boolean)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TrackDao {
    // ---- groupings (library tabs); always missing = 0 ----

    @Query(
        """SELECT parentPath AS path, MAX(parentName) AS name, COUNT(*) AS trackCount
           FROM tracks WHERE missing = 0
           GROUP BY parentPath ORDER BY parentPath COLLATE NOCASE""",
    )
    fun observeFolderGroups(): Flow<List<FolderGroup>>

    @Query(
        """SELECT artist AS name, COUNT(*) AS trackCount
           FROM tracks WHERE missing = 0
           GROUP BY artist ORDER BY artist COLLATE NOCASE""",
    )
    fun observeArtistGroups(): Flow<List<ArtistGroup>>

    @Query(
        """SELECT album AS album, COALESCE(albumArtist, artist) AS artist, COUNT(*) AS trackCount
           FROM tracks WHERE missing = 0
           GROUP BY album, COALESCE(albumArtist, artist)
           ORDER BY album COLLATE NOCASE, COALESCE(albumArtist, artist) COLLATE NOCASE""",
    )
    fun observeAlbumGroups(): Flow<List<AlbumGroup>>

    // ---- per-key track lists (unsorted; sort in Kotlin with TrackSorter) ----

    @Query("SELECT * FROM tracks WHERE missing = 0 AND parentPath = :path")
    fun observeTracksInFolder(path: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE missing = 0 AND artist = :artist")
    fun observeTracksByArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE missing = 0 AND album = :album AND COALESCE(albumArtist, artist) = :artist")
    fun observeTracksInAlbum(album: String, artist: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE missing = 0")
    fun observeLibraryCount(): Flow<Int>

    // ---- lookups (include missing rows; used by playlists / playback) ----

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    fun observeByIds(ids: List<String>): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<TrackEntity>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    // ---- writes ----

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET missing = 1 WHERE id IN (:ids)")
    suspend fun markMissing(ids: List<String>)

    @Query("UPDATE tracks SET missing = 1 WHERE folderId = :folderId")
    suspend fun markFolderMissing(folderId: Long)

    /** Deletes the folder's tracks that no playlist entry references. */
    @Query(
        """DELETE FROM tracks WHERE folderId = :folderId
           AND id NOT IN (SELECT trackId FROM playlist_entries)""",
    )
    suspend fun deleteUnreferencedInFolder(folderId: Long)
}

@Dao
interface PlaylistDao {
    @Query(
        """SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, p.updatedAt AS updatedAt,
                  COUNT(e.id) AS trackCount
           FROM playlists p LEFT JOIN playlist_entries e ON e.playlistId = p.id
           GROUP BY p.id ORDER BY p.name COLLATE NOCASE, p.id""",
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position, id")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    /** Entries joined with their track row (null when the track row is gone); re-emits on tracks changes too. */
    @Transaction
    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position, id")
    fun observeEntriesWithTracks(playlistId: Long): Flow<List<PlaylistEntryWithTrack>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position, id")
    suspend fun getEntries(playlistId: Long): List<PlaylistEntryEntity>

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun deleteEntries(playlistId: Long)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    /** Replaces all entries of a playlist (used after PlaylistOrdering ops; positions already 0..n-1). */
    @Transaction
    suspend fun replaceEntries(playlistId: Long, entries: List<PlaylistEntryEntity>, updatedAt: Long) {
        deleteEntries(playlistId)
        insertEntries(entries.map { it.copy(playlistId = playlistId) })
        touch(playlistId, updatedAt)
    }
}
