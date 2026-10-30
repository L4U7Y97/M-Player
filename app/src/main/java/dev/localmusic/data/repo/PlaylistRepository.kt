package dev.localmusic.data.repo

import dev.localmusic.data.db.LocalMusicDatabase
import dev.localmusic.data.db.PlaylistEntity
import dev.localmusic.data.db.PlaylistEntryEntity
import dev.localmusic.data.db.PlaylistWithCount
import dev.localmusic.data.db.toTrack
import dev.localmusic.domain.PlaylistItem
import dev.localmusic.domain.PlaylistOrdering
import dev.localmusic.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One playlist row: [track] is null when the track row is gone; [missing] covers both gone and missing files. */
data class PlaylistTrack(
    val entryId: Long,
    val position: Int,
    val trackId: String,
    val track: Track?,
) {
    val missing: Boolean get() = track == null || track.missing
}

/** Playlists stored in Room (spec §3, §10). Reorders go through the pure [PlaylistOrdering]. */
class PlaylistRepository(
    private val db: LocalMusicDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.playlistDao()
    private val editMutex = Mutex()

    val playlists: Flow<List<PlaylistWithCount>> = dao.observePlaylists()

    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

    fun entries(id: Long): Flow<List<PlaylistTrack>> = dao.observeEntriesWithTracks(id).map { rows ->
        rows.map { PlaylistTrack(it.entry.id, it.entry.position, it.entry.trackId, it.track?.toTrack()) }
    }

    suspend fun create(name: String): Long {
        val t = now()
        return dao.insertPlaylist(PlaylistEntity(name = name.trim().ifEmpty { "Playlist" }, createdAt = t, updatedAt = t))
    }

    suspend fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) dao.rename(id, clean, now())
    }

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    suspend fun addTracks(id: Long, trackIds: List<String>) = edit(id) { PlaylistOrdering.append(it, trackIds) }

    suspend fun move(id: Long, from: Int, to: Int) = edit(id) { PlaylistOrdering.move(it, from, to) }

    suspend fun remove(id: Long, index: Int) = edit(id) { PlaylistOrdering.remove(it, index) }

    private suspend fun edit(id: Long, op: (List<PlaylistItem>) -> List<PlaylistItem>) = editMutex.withLock {
        val current = dao.getEntries(id).map { PlaylistItem(it.id, it.trackId, it.position) }
        val result = op(current)
        dao.replaceEntries(
            id,
            result.map { PlaylistEntryEntity(id = it.entryId, playlistId = id, trackId = it.trackId, position = it.position) },
            now(),
        )
    }
}
