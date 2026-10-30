package dev.localmusic.data.scan

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log

/** Reads raw tags with [MediaMetadataRetriever]. Never throws: any failure yields all-null [RawTags]. */
class TagReader(private val context: Context) {

    fun read(uri: Uri): RawTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            RawTags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read tags for $uri: ${t.javaClass.simpleName}: ${t.message}")
            RawTags()
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // release() may throw IOException on API 29+; nothing to do.
            }
        }
    }

    private companion object {
        const val TAG = "TagReader"
    }
}
