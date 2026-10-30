package dev.localmusic.data.scan

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import dev.localmusic.data.db.FolderEntity
import dev.localmusic.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Result of scanning one picked folder. */
data class FolderScanResult(val tracks: List<TrackEntity>)

/** Thrown when the folder's tree URI is no longer readable (permission revoked / storage gone). */
class FolderAccessLostException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Walks a picked SAF tree with DocumentsContract (one query per directory, iterative BFS, on IO), see spec §4.
 * Tracks whose (lastModified, size) match an existing row reuse its tags; otherwise tags are read with [TagReader].
 * A file that fails to read never aborts the scan.
 */
class FolderScanner(
    private val resolver: ContentResolver,
    private val tagReader: TagReader,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Dir(val documentId: String, val path: String, val name: String, val depth: Int)

    /**
     * @param existing previously stored tracks keyed by id (any folder), for incremental tag reuse.
     * @param onTrack called after each audio file is processed (for progress).
     * @throws FolderAccessLostException if the root cannot be listed.
     */
    suspend fun scan(
        folder: FolderEntity,
        existing: Map<String, TrackEntity>,
        onTrack: () -> Unit = {},
    ): FolderScanResult = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folder.treeUri)
        val authority = treeUri.authority.orEmpty()
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            throw FolderAccessLostException("Invalid tree URI ${folder.treeUri}", e)
        }
        val out = ArrayList<TrackEntity>()
        val queue = ArrayDeque<Dir>()
        queue.addLast(Dir(rootDocId, folder.displayName, folder.displayName, 0))

        while (queue.isNotEmpty()) {
            ensureActive()
            val dir = queue.removeFirst()
            val children = try {
                listChildren(treeUri, dir.documentId)
            } catch (e: Exception) {
                if (dir.depth == 0) throw FolderAccessLostException("Cannot list ${folder.treeUri}", e)
                Log.w(TAG, "Skipping unreadable directory ${dir.path}: ${e.message}")
                continue
            }
            if (ScanRules.shouldSkipDirectory(dir.depth, children.map { it.name })) {
                Log.d(TAG, "Skipping ${dir.path}: contains ${ScanRules.NO_MEDIA_FILE}")
                continue
            }
            for (child in children) {
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childDepth = dir.depth + 1
                    if (ScanRules.shouldVisitDirectory(child.name) &&
                        ScanRules.shouldDescend(childDepth, folder.includeSubfolders)
                    ) {
                        queue.addLast(Dir(child.documentId, ScanRules.childPath(dir.path, child.name), child.name, childDepth))
                    }
                } else if (ScanRules.isAudioFile(child.name)) {
                    try {
                        out += buildTrack(folder, treeUri, authority, dir, child, existing)
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping ${child.name}: ${e.message}")
                    }
                    onTrack()
                }
            }
        }
        FolderScanResult(out)
    }

    private fun buildTrack(
        folder: FolderEntity,
        treeUri: Uri,
        authority: String,
        dir: Dir,
        child: Child,
        existing: Map<String, TrackEntity>,
    ): TrackEntity {
        val id = "$authority|${child.documentId}"
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
        val old = existing[id]
        val dateAdded = old?.dateAdded ?: if (child.lastModified > 0) child.lastModified else now()
        if (old != null && old.lastModified == child.lastModified && old.size == child.size) {
            return old.copy(
                uri = uri.toString(),
                folderId = folder.id,
                parentPath = dir.path,
                parentName = dir.name,
                fileName = child.name,
                missing = false,
            )
        }
        val tags = TagFallbacks.resolve(tagReader.read(uri), child.name, dir.name)
        return TrackEntity(
            id = id,
            uri = uri.toString(),
            folderId = folder.id,
            parentPath = dir.path,
            parentName = dir.name,
            fileName = child.name,
            title = tags.title,
            artist = tags.artist,
            album = tags.album,
            albumArtist = tags.albumArtist,
            trackNumber = tags.trackNumber,
            discNumber = tags.discNumber,
            durationMs = tags.durationMs,
            year = tags.year,
            dateAdded = dateAdded,
            size = child.size,
            lastModified = child.lastModified,
            missing = false,
        )
    }

    private data class Child(
        val documentId: String,
        val name: String,
        val mimeType: String?,
        val lastModified: Long,
        val size: Long,
    )

    private fun listChildren(treeUri: Uri, parentDocId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val cursor = resolver.query(childrenUri, PROJECTION, null, null, null)
            ?: throw IllegalStateException("Null cursor for $childrenUri")
        val result = ArrayList<Child>()
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (c.moveToNext()) {
                val docId = c.getString(idCol) ?: continue
                result += Child(
                    documentId = docId,
                    name = c.getString(nameCol) ?: docId.substringAfterLast('/'),
                    mimeType = c.getString(mimeCol),
                    lastModified = if (modCol >= 0 && !c.isNull(modCol)) c.getLong(modCol) else 0L,
                    size = if (sizeCol >= 0 && !c.isNull(sizeCol)) c.getLong(sizeCol) else 0L,
                )
            }
        }
        return result
    }

    companion object {
        private const val TAG = "FolderScanner"
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
