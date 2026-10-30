package dev.localmusic.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.room.withTransaction
import dev.localmusic.data.db.AlbumGroup
import dev.localmusic.data.db.ArtistGroup
import dev.localmusic.data.db.FolderEntity
import dev.localmusic.data.db.FolderGroup
import dev.localmusic.data.db.LocalMusicDatabase
import dev.localmusic.data.db.TrackEntity
import dev.localmusic.data.db.toTrack
import dev.localmusic.data.scan.FolderAccessLostException
import dev.localmusic.data.scan.FolderScanner
import dev.localmusic.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Progress of a library rescan (Settings shows a progress line while [running]). */
data class ScanState(val running: Boolean = false, val scannedCount: Int = 0)

/**
 * Folders, tracks, groupings and rescans (spec §3, §4). All DB / SAF work happens off the main thread.
 */
class LibraryRepository(
    context: Context,
    private val db: LocalMusicDatabase,
    private val scanner: FolderScanner,
    private val appScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val folderDao = db.folderDao()
    private val trackDao = db.trackDao()
    private val scanMutex = Mutex()

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** Ids of folders whose persisted permission is gone (UI shows "Access lost"). Updated by each rescan. */
    private val _accessLost = MutableStateFlow<Set<Long>>(emptySet())
    val accessLost: StateFlow<Set<Long>> = _accessLost.asStateFlow()

    // ---- observation ----------------------------------------------------------------------------------

    val folders: Flow<List<FolderEntity>> = folderDao.observeAll()
    val folderGroups: Flow<List<FolderGroup>> = trackDao.observeFolderGroups()
    val artistGroups: Flow<List<ArtistGroup>> = trackDao.observeArtistGroups()
    val albumGroups: Flow<List<AlbumGroup>> = trackDao.observeAlbumGroups()
    val libraryTrackCount: Flow<Int> = trackDao.observeLibraryCount()

    /** Unsorted; sort with TrackSorter. */
    fun tracksInFolder(path: String): Flow<List<Track>> =
        trackDao.observeTracksInFolder(path).map { list -> list.map { it.toTrack() } }

    fun tracksByArtist(artist: String): Flow<List<Track>> =
        trackDao.observeTracksByArtist(artist).map { list -> list.map { it.toTrack() } }

    /** [artist] = coalesce(albumArtist, artist), as in [AlbumGroup.artist]. */
    fun tracksInAlbum(album: String, artist: String): Flow<List<Track>> =
        trackDao.observeTracksInAlbum(album, artist).map { list -> list.map { it.toTrack() } }

    /**
     * Tracks for [ids] in the given order (duplicates repeated). Includes missing tracks (check [Track.missing]);
     * ids with no row at all are omitted.
     */
    suspend fun tracksByIds(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val byId = HashMap<String, TrackEntity>()
        ids.distinct().chunked(SQL_CHUNK).forEach { chunk -> trackDao.getByIds(chunk).forEach { byId[it.id] = it } }
        return ids.mapNotNull { byId[it]?.toTrack() }
    }

    // ---- folder management ----------------------------------------------------------------------------

    /**
     * Persists read permission for [treeUri] (from OpenDocumentTree), stores the folder (includeSubfolders = true)
     * and rescans. Returns the folder id (existing id if the tree was already added).
     */
    suspend fun addFolder(treeUri: Uri): Long {
        try {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "takePersistableUriPermission failed for $treeUri", e)
        }
        val key = treeUri.toString()
        val id = folderDao.getByTreeUri(key)?.id ?: run {
            val name = withContext(Dispatchers.IO) { queryDisplayName(treeUri) }
            val inserted = folderDao.insert(
                FolderEntity(treeUri = key, displayName = name, includeSubfolders = true, addedAt = now()),
            )
            if (inserted > 0) inserted else folderDao.getByTreeUri(key)?.id ?: -1L
        }
        rescanAll()
        return id
    }

    /**
     * Releases the permission, deletes the folder row and its tracks not referenced by any playlist entry;
     * the remaining (referenced) tracks are marked missing. Rescans if other folders remain (overlapping trees).
     */
    suspend fun removeFolder(folderId: Long) {
        val remaining = scanMutex.withLock {
            val folder = folderDao.getById(folderId) ?: return
            try {
                resolver.releasePersistableUriPermission(Uri.parse(folder.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w(TAG, "releasePersistableUriPermission failed for ${folder.treeUri}: ${e.message}")
            }
            db.withTransaction {
                trackDao.deleteUnreferencedInFolder(folderId)
                trackDao.markFolderMissing(folderId)
                folderDao.delete(folderId)
            }
            _accessLost.value = _accessLost.value - folderId
            folderDao.getAll()
        }
        if (remaining.isNotEmpty()) rescanAll()
    }

    suspend fun setIncludeSubfolders(folderId: Long, include: Boolean) {
        folderDao.setIncludeSubfolders(folderId, include)
        rescanAll()
    }

    /** Fire-and-forget rescan on the app scope (e.g. "Rescan" button). */
    fun rescanAllAsync(): Job = appScope.launch { rescanAll() }

    fun hasPersistedPermission(treeUri: String): Boolean =
        resolver.persistedUriPermissions.any { it.isReadPermission && it.uri.toString() == treeUri }

    /**
     * Rescans every folder. Seen tracks are upserted with missing = 0 (first folder that sees a track wins);
     * previously known tracks of successfully scanned folders that were not seen are marked missing; folders whose
     * permission is gone get all their tracks marked missing and are reported in [accessLost].
     */
    suspend fun rescanAll() {
        scanMutex.withLock {
            val counter = AtomicInteger(0)
            _scanState.value = ScanState(running = true, scannedCount = 0)
            try {
                val folders = folderDao.getAll()
                val existing = trackDao.getAll().associateBy { it.id }
                val seen = HashSet<String>()
                val lost = HashSet<Long>()
                for (folder in folders) {
                    if (!hasPersistedPermission(folder.treeUri)) {
                        lost += folder.id
                        trackDao.markFolderMissing(folder.id)
                        continue
                    }
                    val result = try {
                        scanner.scan(folder, existing) {
                            _scanState.value = ScanState(running = true, scannedCount = counter.incrementAndGet())
                        }
                    } catch (e: FolderAccessLostException) {
                        Log.w(TAG, "Access lost for ${folder.treeUri}: ${e.message}")
                        lost += folder.id
                        trackDao.markFolderMissing(folder.id)
                        continue
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Access lost for ${folder.treeUri}: ${e.message}")
                        lost += folder.id
                        trackDao.markFolderMissing(folder.id)
                        continue
                    }
                    val fresh = result.tracks.filter { seen.add(it.id) }
                    fresh.chunked(SQL_CHUNK).forEach { trackDao.upsertAll(it) }
                }
                val scannedFolderIds = folders.map { it.id }.toSet() - lost
                val toMarkMissing = existing.values
                    .filter { it.folderId in scannedFolderIds && it.id !in seen && !it.missing }
                    .map { it.id }
                toMarkMissing.chunked(SQL_CHUNK).forEach { trackDao.markMissing(it) }
                _accessLost.value = lost
            } finally {
                _scanState.value = ScanState(running = false, scannedCount = counter.get())
            }
        }
    }

    private fun queryDisplayName(treeUri: Uri): String {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            null
        }
        if (docId != null) {
            try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                resolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it } }
            } catch (e: Exception) {
                Log.w(TAG, "Display name query failed for $treeUri: ${e.message}")
            }
            val fromId = docId.substringAfterLast(':').substringAfterLast('/')
            if (fromId.isNotBlank()) return fromId
        }
        return treeUri.lastPathSegment ?: "Folder"
    }

    private companion object {
        const val TAG = "LibraryRepository"
        const val SQL_CHUNK = 500
    }
}
