package dev.localmusic.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.localmusic.ui.detail.TrackListScreen
import dev.localmusic.ui.detail.TrackSource
import dev.localmusic.ui.components.appContainer
import dev.localmusic.ui.library.LibraryScreen
import dev.localmusic.ui.nowplaying.NowPlayingScreen
import dev.localmusic.ui.playlists.PlaylistDetailScreen
import dev.localmusic.ui.queue.QueueScreen
import dev.localmusic.ui.settings.EqualizerScreen
import dev.localmusic.ui.settings.FoldersScreen
import dev.localmusic.ui.settings.SettingsScreen
import dev.localmusic.ui.settings.ThemeScreen

/**
 * Root navigation (spec §10). Root has testTagsAsResourceId so list-row testTags appear as resource-ids.
 * The last opened track list (folder / artist / album / playlist) is remembered and reopened on the next launch.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val uiState = appContainer().uiStateStore
    // Read before the destination listener below runs (it clears the route when it sees the Library screen).
    val savedRoute = remember { uiState.lastListRoute }
    var restored by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, args ->
            when (destination.route) {
                Routes.LIBRARY -> uiState.lastListRoute = null
                Routes.FOLDER -> uiState.lastListRoute = Routes.folder(args?.getString("path").orEmpty())
                Routes.ARTIST -> uiState.lastListRoute = Routes.artist(args?.getString("name").orEmpty())
                Routes.ALBUM -> uiState.lastListRoute =
                    Routes.album(args?.getString("album").orEmpty(), args?.getString("artist").orEmpty())
                Routes.PLAYLIST -> uiState.lastListRoute = Routes.playlist(args?.getLong("id") ?: -1L)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    LaunchedEffect(Unit) {
        if (restored) return@LaunchedEffect // recreated: NavController restored its own back stack
        restored = true
        savedRoute?.let { navController.navigate(it) }
    }

    val back: () -> Unit = { navController.navigateUp() }
    val openNowPlaying: () -> Unit = { navController.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenFolder = { navController.navigate(Routes.folder(it)) },
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { album, artist -> navController.navigate(Routes.album(album, artist)) },
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenNowPlaying = openNowPlaying,
            )
        }
        composable(Routes.FOLDER, arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            TrackListScreen(TrackSource.Folder(path), onBack = back, onOpenNowPlaying = openNowPlaying)
        }
        composable(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            val name = entry.arguments?.getString("name").orEmpty()
            TrackListScreen(TrackSource.Artist(name), onBack = back, onOpenNowPlaying = openNowPlaying)
        }
        composable(
            Routes.ALBUM,
            arguments = listOf(
                navArgument("album") { type = NavType.StringType },
                navArgument("artist") { type = NavType.StringType },
            ),
        ) { entry ->
            val album = entry.arguments?.getString("album").orEmpty()
            val artist = entry.arguments?.getString("artist").orEmpty()
            TrackListScreen(TrackSource.Album(album, artist), onBack = back, onOpenNowPlaying = openNowPlaying)
        }
        composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            PlaylistDetailScreen(id, onBack = back, onOpenNowPlaying = openNowPlaying)
        }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                onBack = back,
                onOpenQueue = { navController.navigate(Routes.QUEUE) { launchSingleTop = true } },
            )
        }
        composable(Routes.QUEUE) { QueueScreen(onBack = back) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onOpenFolders = { navController.navigate(Routes.SETTINGS_FOLDERS) },
                onOpenEqualizer = { navController.navigate(Routes.SETTINGS_EQUALIZER) },
                onOpenTheme = { navController.navigate(Routes.SETTINGS_THEME) },
            )
        }
        composable(Routes.SETTINGS_FOLDERS) { FoldersScreen(onBack = back) }
        composable(Routes.SETTINGS_EQUALIZER) { EqualizerScreen(onBack = back) }
        composable(Routes.SETTINGS_THEME) { ThemeScreen(onBack = back) }
    }
}
