package dev.localmusic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, TrackEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LocalMusicDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "local_music.db"

        fun build(context: Context): LocalMusicDatabase =
            Room.databaseBuilder(context.applicationContext, LocalMusicDatabase::class.java, NAME).build()
    }
}
