package dev.localmusic.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.localmusic.domain.Track

/** Track <-> MediaItem (spec §5). mediaId = track id; the URI is duplicated in RequestMetadata.mediaUri. */
object MediaItemMapper {

    fun toMediaItem(track: Track): MediaItem {
        val uri = Uri.parse(track.uri)
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setAlbumArtist(track.albumArtist)
                    .setTrackNumber(track.trackNumber)
                    .setDiscNumber(track.discNumber)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    /**
     * Controllers may strip `localConfiguration` when items cross the session boundary, so the session rebuilds
     * each item's URI from `requestMetadata.mediaUri`.
     */
    fun restoreUri(item: MediaItem): MediaItem {
        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri ?: return item
        return item.buildUpon().setUri(uri).build()
    }
}
