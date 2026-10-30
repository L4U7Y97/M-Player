package dev.localmusic

import android.content.Context
import android.media.AudioManager
import dev.localmusic.data.db.LocalMusicDatabase
import dev.localmusic.data.repo.LibraryRepository
import dev.localmusic.data.repo.PlaylistRepository
import dev.localmusic.data.scan.FolderScanner
import dev.localmusic.data.scan.TagReader
import dev.localmusic.playback.AudioEffectsController
import dev.localmusic.playback.PlayerConnection
import dev.localmusic.settings.SettingsRepository
import dev.localmusic.settings.UiStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI (spec §1/§2): lazy process-wide singletons.
 * [audioEffects] owns the Equalizer/BassBoost on [audioSessionId] (spec §7).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Long-lived scope for fire-and-forget work (rescans). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LocalMusicDatabase by lazy { LocalMusicDatabase.build(appContext) }

    val tagReader: TagReader by lazy { TagReader(appContext) }

    val folderScanner: FolderScanner by lazy { FolderScanner(appContext.contentResolver, tagReader) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(appContext, database, folderScanner, appScope)
    }

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext, appScope) }

    /** Library tab and last opened track list, restored on launch. */
    val uiStateStore: UiStateStore by lazy { UiStateStore(appContext) }

    /** MediaController wrapper; connected by MainActivity while it is started. */
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }

    /** App-generated audio session id shared by the service player and the audio effects. */
    val audioSessionId: Int by lazy {
        (appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
    }

    /** Equalizer + bass boost on [audioSessionId]; started by the service and by the Equalizer screen. */
    val audioEffects: AudioEffectsController by lazy { AudioEffectsController(audioSessionId, settingsRepository) }
}
