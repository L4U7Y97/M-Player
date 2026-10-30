package dev.localmusic

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import dev.localmusic.settings.ThemeMode
import dev.localmusic.widget.WidgetRenderer
import dev.localmusic.widget.WidgetState
import dev.localmusic.widget.WidgetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LocalMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A fresh process means the playback service is not running: any stored "active" widget state is stale
        // (the service re-publishes it as soon as it is created).
        if (WidgetStateStore.read(this).active) {
            WidgetStateStore.write(this, WidgetState())
            WidgetRenderer.updateAll(this, WidgetState())
        }
        syncAppNightMode()
    }

    /**
     * API 31+: mirror the app theme setting into the per-app night mode, which the system persists and uses for the
     * launch splash screen and the window background (values-night theme). Without it a forced Dark theme on a light
     * system showed a white splash for ~2 s on cold start. MainActivity handles `uiMode` config changes itself, so a
     * theme switch does not recreate it (Compose re-themes live). Older APIs keep the system-driven splash.
     */
    private fun syncAppNightMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            container.settingsRepository.settings
                .map { it.themeMode }
                .distinctUntilChanged()
                .collect { mode ->
                    uiModeManager.setApplicationNightMode(
                        when (mode) {
                            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                        },
                    )
                }
        }
    }
}

/** Convenience accessor: `context.appContainer`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as LocalMusicApp).container
