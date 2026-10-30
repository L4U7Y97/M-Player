# Local Music — architecture spec

Scope is fixed by `docs/intent.md`. This file decides *how*. Tasks live in `docs/tasks.md`.

## 1. Project & build

- App name **M-Player**; `applicationId` = `namespace` = `dev.localmusic`; single module `:app`.
- `minSdk 26`, `compileSdk 35`, `targetSdk 35`. All libraries below were checked: every AAR (incl. transitive
  androidx.core/activity) declares `minCompileSdk <= 35` and `minAGP <= 8.6`. **Do not bump any library** without
  re-checking — newer core (1.17+), activity (1.11+), Media3 (1.9+) need compileSdk 36.
- Gradle 8.14.3 wrapper (`distributionUrl=...gradle-8.14.3-bin.zip`, already cached), Kotlin DSL, version catalog
  `gradle/libs.versions.toml`. Repos: `google()`, `mavenCentral()`, `gradlePluginPortal()` (plugins only).
- Java/Kotlin target 17: `compileOptions { source/targetCompatibility = JavaVersion.VERSION_17 }` and
  `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`. **No `jvmToolchain(..)`** (no JDK 17 installed;
  build runs on JDK 21). `buildFeatures { compose = true }`. No minify for debug.
- `gradle.properties`: `android.useAndroidX=true`, `android.nonTransitiveRClass=true`,
  `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`, `kotlin.code.style=official`.
- No Hilt/Dagger, no kotlinx-serialization: manual DI via `AppContainer`, string nav routes.

| catalog key | coordinates | version |
|---|---|---|
| agp | com.android.application (plugin) | 8.13.0 |
| kotlin | org.jetbrains.kotlin.android + org.jetbrains.kotlin.plugin.compose | 2.2.20 |
| ksp | com.google.devtools.ksp | 2.2.20-2.0.4 |
| composeBom | androidx.compose:compose-bom (ui 1.9.4, material3 1.4.0, icons-extended 1.7.8) | 2025.10.01 |
| — | compose ui, ui-tooling-preview, material3, material-icons-extended, foundation (no versions, from BOM); ui-tooling as debugImplementation | BOM |
| activityCompose | androidx.activity:activity-compose | 1.10.1 |
| coreKtx | androidx.core:core-ktx | 1.16.0 |
| lifecycle | androidx.lifecycle:lifecycle-runtime-ktx, -runtime-compose, -viewmodel-compose | 2.9.4 |
| navigation | androidx.navigation:navigation-compose | 2.9.5 |
| media3 | androidx.media3:media3-exoplayer, media3-session | 1.8.0 |
| room | androidx.room:room-runtime, room-ktx, room-compiler (ksp) | 2.8.3 |
| datastore | androidx.datastore:datastore-preferences | 1.1.7 |
| coroutines | org.jetbrains.kotlinx:kotlinx-coroutines-android, -guava (await ListenableFuture), -test | 1.10.2 |
| junit | junit:junit (testImplementation) | 4.13.2 |

Room: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` or `exportSchema = false` (pick one). No widget
library: the widget uses RemoteViews (see §8).

## 2. Package layout (`app/src/main/java/dev/localmusic/`)

```
LocalMusicApp.kt          Application; creates AppContainer (lazy singletons)
AppContainer.kt           db, repositories, SettingsRepository, PlayerConnection, AudioEffectsController, audioSessionId
MainActivity.kt           ComponentActivity, setContent { LocalMusicTheme { AppNavHost() } }, asks POST_NOTIFICATIONS
data/db/                  Room: entities, DAOs, LocalMusicDatabase
data/scan/                FolderScanner (SAF walk), TagReader (MediaMetadataRetriever), ScanRules*, TagFallbacks*
data/repo/                LibraryRepository (folders, tracks, groupings, rescan), PlaylistRepository
domain/                   TrackSorter*, SortSpec*, QueueModel*, RepeatCycler*, PlaylistOrdering*, Track (UI model)
playback/                 PlaybackService, PlayerConnection, MediaItemMapper, AudioEffectsController
settings/                 SettingsRepository (DataStore), AppSettings, SettingsMapper*, EqSettings*
ui/theme/                 LocalMusicTheme, Accent palettes
ui/nav/                   AppNavHost, Routes
ui/library/ ui/detail/ ui/nowplaying/ ui/queue/ ui/playlists/ ui/settings/ ui/components/ (MiniPlayer, TrackRow, dialogs)
widget/                   NowPlayingWidgetProvider, WidgetRenderer, WidgetStateStore
```
`*` = pure Kotlin, **no android.* imports**, unit-tested by the tester in `app/src/test/`.

## 3. Data model (Room, `LocalMusicDatabase` v1)

- `folders(id Long PK auto, treeUri String UNIQUE, displayName, includeSubfolders Boolean, addedAt Long)`
- `tracks(id String PK, uri String, folderId Long, parentPath String, parentName String, fileName String,
  title, artist, album, albumArtist String?, trackNumber Int?, discNumber Int?, durationMs Long, year Int?,
  dateAdded Long, size Long, lastModified Long, missing Boolean)` — index on folderId, artist, album, parentPath.
  - `id = "<authority>|<documentId>"` (documentId is stable across tree URIs → overlapping picked folders dedupe).
  - `uri` = `DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)` — what ExoPlayer plays.
  - `parentPath` = display path relative to the picked root, root name included (e.g. `Music/Rock/AlbumX`);
    `parentName` = last segment. `dateAdded` = `COLUMN_LAST_MODIFIED`, or first-seen time if 0.
- `playlists(id Long PK auto, name String, createdAt Long, updatedAt Long)`
- `playlist_entries(id Long PK auto, playlistId Long FK→playlists CASCADE, trackId String, position Int)`;
  index (playlistId, position). **No FK on trackId** (entries survive track removal and show as missing).
- Library queries always filter `missing = 0`. Groupings via DAO queries (Flow):
  Folders tab = distinct `parentPath` (+ count); Artists = distinct `artist`; Albums = distinct (`album`,
  `coalesce(albumArtist, artist)`). Detail lists are tracks filtered by that key, then sorted in Kotlin by `TrackSorter`.

## 4. Folders & scanning

- Add folder: `ActivityResultContracts.OpenDocumentTree()` → `contentResolver.takePersistableUriPermission(uri,
  FLAG_GRANT_READ_URI_PERMISSION)` → insert folder (includeSubfolders default **true**) → scan. Remove folder:
  `releasePersistableUriPermission`, delete folder row, delete its tracks not referenced by any playlist entry,
  mark the rest `missing = 1`.
- `FolderScanner.scan(folder)` walks `DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)` with a
  single `ContentResolver.query` per directory, projection `DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, LAST_MODIFIED, SIZE`
  (no DocumentFile). Iterative BFS on `Dispatchers.IO`. Root docId = `DocumentsContract.getTreeDocumentId(treeUri)`.
- `ScanRules` (pure): `SUPPORTED_EXTENSIONS = mp3, m4a, aac, flac, ogg, oga, opus, wav` (case-insensitive);
  `isAudioFile(name)` (false for names starting with `.`); `shouldDescend(depth, includeSubfolders)` → root (depth 0)
  always listed, children only if includeSubfolders; skip dirs starting with `.`; `childPath(parentPath, name)`.
- Incremental: if an existing row has same `lastModified` and `size`, reuse its tags (no retriever call).
  Otherwise `TagReader` (`MediaMetadataRetriever.setDataSource(context, uri)`, always `release()` in finally;
  any exception → raw tags all null) reads TITLE, ARTIST, ALBUMARTIST, ALBUM, CD_TRACK_NUMBER, DISC_NUMBER,
  DURATION, YEAR.
- `TagFallbacks.resolve(raw, fileName, parentName)` (pure): blank/whitespace → null then fallback:
  title → file name without extension; artist → albumArtist → `"Unknown artist"`; album → `parentName`;
  trackNumber/disc parse `"3"`, `"3/12"`, `" 03 "` → 3, garbage → null; year first 4 digits; duration null → 0.
- Rescan (all folders, button + after add): upsert every seen track with `missing = 0`; every previously known
  track of a scanned folder not seen → `missing = 1`. Folder whose permission is gone (`SecurityException` / not in
  `persistedUriPermissions`) → its tracks marked missing, folder shown with "Access lost" text. Expose
  `scanState: StateFlow<ScanState(running, scannedCount)>`; Settings shows a progress line while running.

## 5. Playback

- `PlaybackService : MediaSessionService` (manifest: `exported=true`, `foregroundServiceType="mediaPlayback"`,
  intent-filter actions `androidx.media3.session.MediaSessionService` and `android.media.browse.MediaBrowserService`).
- `onCreate`: `ExoPlayer.Builder(this).setAudioAttributes(AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC),
  /*handleAudioFocus*/ true).setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build()`, then
  `player.setAudioSessionId(container.audioSessionId)`; `MediaSession.Builder(this, player)
  .setSessionActivity(PendingIntent → MainActivity, FLAG_IMMUTABLE).setCallback(callback).build()`.
  Default `DefaultMediaNotificationProvider` gives notification + lock screen controls; MediaSession gives
  Bluetooth/headset buttons. `onTaskRemoved`: stop self if `!playWhenReady || mediaItemCount == 0`.
  `onDestroy`: release session + player, clear widget "active" state.
- Media items: `MediaItemMapper.toMediaItem(track)` sets `mediaId = track.id`, `uri`, `RequestMetadata.mediaUri =
  uri`, `MediaMetadata(title, artist, albumTitle, trackNumber)`. Session callback `onAddMediaItems` (and
  `onSetMediaItems` default delegating to it) rebuilds each item with `setUri(item.requestMetadata.mediaUri)` —
  controllers may strip `localConfiguration`.
- `PlayerConnection` (process singleton): `MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx,
  PlaybackService::class.java))).buildAsync().await()`. Exposes `StateFlow<PlayerUiState>` (current track meta,
  isPlaying, positionMs/durationMs polled every 500 ms while playing, shuffle, repeatMode, queue items, current
  index). Commands: `playTracks(list, startIndex, shuffle)`, `togglePlayPause`, `next` (`seekToNext`), `previous`
  (`seekToPrevious`), `seekTo`, `toggleShuffle`, `cycleRepeat` (via `RepeatCycler`: OFF→ALL→ONE→OFF),
  `playQueueIndex(i)`, `moveQueueItem(from,to)` → `moveMediaItem`, `removeQueueItem(i)` → `removeMediaItem`,
  `addToQueue(tracks)`.
- **Gapless rule:** a list is started with exactly one `setMediaItems(items, startIndex, 0)` + `prepare()` + `play()`.
  Never `stop()/prepare()/setMediaItem()` per track, never rebuild the list for queue edits (use move/remove/add,
  which don't interrupt playback). ExoPlayer then plays consecutive items gaplessly (MP3 encoder delay from
  LAME/iTunes headers is handled by ExoPlayer). No clipping/ConcatenatingMediaSource.
- Shuffle = `shuffleModeEnabled` (player's ShuffleOrder). "Shuffle all" in a list = `playTracks(list,
  random index, shuffle = true)`. Queue screen shows items in **play order** (walk `timeline.getFirstWindowIndex /
  getNextWindowIndex(i, REPEAT_MODE_OFF, shuffle)`); while shuffle is on, reorder controls are disabled, remove works.
- Skip silence: service collects `settings.skipSilence` and calls `player.skipSilenceEnabled = value`.
- Persistence (Phase 8, optional): save queue ids/index/position/shuffle/repeat to DataStore on pause, item
  transition and destroy; implement `MediaSession.Callback.onPlaybackResumption` from it. Only then add Media3's
  `MediaButtonReceiver` to the manifest (without resumption it can crash on headset play with a dead service).

## 6. Sorting & pure domain logic

- `SortSpec(field: SortField, ascending: Boolean)`; `SortField = TRACK_NUMBER, TITLE, FILE_NAME, DATE_ADDED`.
  One global SortSpec persisted in DataStore (default TRACK_NUMBER asc), chosen from each detail list's "Sort" menu.
- `TrackSorter.sort(tracks, spec)`: TRACK_NUMBER = (discNumber nulls-as-1, trackNumber) — tracks with null
  trackNumber always **last** in both directions, ties → fileName; TITLE/FILE_NAME = case-insensitive
  (`String.CASE_INSENSITIVE_ORDER`), ties → fileName; DATE_ADDED = dateAdded, ties → fileName. Descending reverses the
  primary key only (tie-break stays fileName asc). Stable. Works on a plain `SortableTrack` interface/data class.
- `QueueModel` (pure mirror of player queue semantics): `move(items, currentIndex, from, to)` and
  `remove(items, currentIndex, index)` → new (items, currentIndex) with current index adjusted the way ExoPlayer
  does; `playOrder(count, current, shuffledOrder?)`. UI uses it for optimistic updates; tests pin the semantics.
- `PlaylistOrdering`: `move(entries, from, to)`, `remove(entries, index)`, `append(entries, trackIds)` → positions
  always renumbered 0..n-1; duplicates allowed; out-of-range = no-op.
- `RepeatCycler.next(mode)`; `SettingsMapper` (see §7).

## 7. Settings, equalizer, themes (DataStore `settings.preferences_pb`)

- `AppSettings(themeMode: SYSTEM|LIGHT|DARK, pureBlack, accent: Accent, dynamicColor, skipSilence, sort: SortSpec,
  eq: EqSettings)`. `EqSettings(enabled, presetIndex /* -1 = custom */, bandLevelsMb: List<Int>,
  bassBoostEnabled, bassBoostStrength 0..1000)`. Defaults: SYSTEM, false, PURPLE, true, false, TRACK_NUMBER asc,
  EQ off/custom/flat, bass off/0.
- `SettingsMapper` (pure): enum ⇄ stored string with safe default for unknown values; band levels ⇄ CSV
  (`"-300,0,150"`), invalid entries → 0, clamped to `[minMb, maxMb]`; strength clamped 0..1000.
- `AudioEffectsController` (singleton in AppContainer): `audioSessionId` generated once via
  `AudioManager.generateAudioSessionId()` and used by the service player, so effects are created independently
  of the service. Creates `Equalizer(0, id)` + `BassBoost(0, id)` lazily in try/catch; on failure
  `available = false` and the EQ screen shows "Equalizer not available on this device". Exposes
  `EqCapabilities(bandCount, centerFreqsHz, minMb, maxMb, presetNames)`. Collects `settings.eq` and applies:
  enabled → `equalizer.enabled`; presetIndex ≥ 0 → `usePreset`, then reads back band levels and saves them; custom →
  `setBandLevel` per band; bass boost `enabled` + `setStrength` (if `strengthSupported`). Re-applies whenever
  re-created (e.g. after `release`).
- Theme: `LocalMusicTheme(settings)`: dark = mode or `isSystemInDarkTheme()`; if `dynamicColor && SDK >= 31` →
  `dynamicDark/LightColorScheme`, else accent scheme (5 hand-picked seeds: PURPLE, BLUE, GREEN, ORANGE, PINK, each
  with a light and dark `ColorScheme`). Pure black (only in dark) → `background = surface = Color.Black`.
  Theme changes apply live (no restart).

## 8. Widget — RemoteViews (`AppWidgetProvider`), no Glance: zero extra deps and synchronous updates pushed from the service.

- Layout: title, artist, Previous / Play-Pause / Next `ImageButton`s, root click → MainActivity. Resizable
  (`resizeMode="horizontal|vertical"`, minWidth 180dp, minHeight 40dp, targetCellWidth 4, targetCellHeight 1).
- Buttons: `PendingIntent.getForegroundService(ctx, code, Intent(Intent.ACTION_MEDIA_BUTTON)
  .setComponent(PlaybackService) .putExtra(EXTRA_KEY_EVENT, KeyEvent(ACTION_DOWN, KEYCODE_MEDIA_*)), IMMUTABLE)`
  (MediaSessionService dispatches media-button intents to the session). If the widget state is not "active"
  (service not running / empty queue) every button opens MainActivity instead (avoids FGS-start-without-media crash).
- `WidgetStateStore` (SharedPreferences): active, title, artist, isPlaying. Service listener
  (`onMediaItemTransition`, `onIsPlayingChanged`, `onMediaMetadataChanged`) writes it and calls
  `WidgetRenderer.updateAll(ctx)` → `AppWidgetManager.updateAppWidget(ids, views)`. Provider `onUpdate` renders
  from the store (shows "Not playing" when inactive).

## 9. Manifest & permissions

- `POST_NOTIFICATIONS` (requested at first launch on 33+, denial is non-fatal), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. No storage/media permissions (SAF only).
- **No INTERNET.** Add `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` and the
  same for `ACCESS_NETWORK_STATE`. Verify after each build:
  `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` contains no
  `INTERNET`, and `"%ANDROID_HOME%\build-tools\35.0.0\aapt.exe" dump permissions app\build\outputs\apk\debug\app-debug.apk`
  lists none.

## 10. Screens & navigation (Material 3, `NavHost`, string routes, args `Uri.encode`d)

- `library` — TopAppBar "Local Music" (no search), action icon "Settings". `TabRow` tabs **Folders / Artists /
  Albums / Playlists**. Empty library → text "No music folders yet" + button "Add folder". Rows show name + track
  count. MiniPlayer at bottom of library + detail screens when a track is loaded.
- `folder/{path}`, `artist/{name}`, `album/{album}/{artist}` → `TrackListScreen`: title, buttons **Play all** /
  **Shuffle all**, "Sort" menu (4 fields + Ascending/Descending items, current checked), rows (title, artist,
  duration) — tap = play list from that index; row "More options" → "Add to queue", "Add to playlist".
- `now_playing` — title, artist, album, seek `Slider` + elapsed/total text, Previous, Play/Pause, Next, Shuffle
  (toggle), Repeat (3-state), Queue, Add to playlist. Opened by tapping the MiniPlayer.
- `queue` — LazyColumn in play order, current item highlighted; tap = jump; per row "Move up", "Move down",
  "Remove from queue" (drag handle optional, buttons are the required path).
- `playlists` tab → list + FAB "New playlist" (dialog: text field "Playlist name", "Create"/"Cancel"); row menu
  "Rename playlist" / "Delete playlist" (confirm dialog). `playlist/{id}` = track list with Play all / Shuffle all,
  per-row "Move up" / "Move down" / "Remove from playlist"; missing tracks greyed with text "Missing" and skipped
  when playing. "Add to playlist" dialog lists playlists + "New playlist".
- `settings` → rows: "Music folders", "Equalizer", "Theme", switch "Skip silence".
  `settings/folders`: FAB/button "Add folder", per folder row: name, switch "Include subfolders", "Remove folder";
  top action "Rescan" + scan progress text. `settings/equalizer`: switch "Equalizer enabled", "Preset" dropdown
  (device presets + "Custom"), one vertical/horizontal slider per band labelled "<freq> Hz", switch "Bass boost",
  slider "Bass boost strength". `settings/theme`: radio "Follow system" / "Light" / "Dark", switches "Pure black",
  "Dynamic color" (hidden < API 31), accent swatches.
- State: one ViewModel per screen built from `AppContainer` via `viewModelFactory { initializer { … } }`.

## 11. QA hooks (contract — QA taps by these exact strings)

Root composable: `Modifier.semantics { testTagsAsResourceId = true }`; list rows get testTags `folder_row`,
`artist_row`, `album_row`, `playlist_row`, `track_row`, `queue_row` (show up as resource-id).

| Element | contentDescription / text |
|---|---|
| Play / pause (mini player, Now Playing) | `Play` when paused, `Pause` when playing |
| Next / previous | `Next`, `Previous` |
| Shuffle toggle (Now Playing) | `Shuffle` (IconToggleButton → uiautomator `checked=true/false`) |
| Repeat | `Repeat off` / `Repeat all` / `Repeat one` |
| Queue button / seek bar | `Queue`, `Seek bar` |
| Mini player body | `Open now playing` |
| Navigate up | `Back` |
| Library settings | `Settings` |
| Sort menu / row overflow | `Sort`, `More options` |
| List buttons (text) | `Play all`, `Shuffle all` |
| Queue row | `Move up`, `Move down`, `Remove from queue` |
| Playlist | `New playlist`, `Rename playlist`, `Delete playlist`, `Remove from playlist`, `Add to playlist`, `Move up`, `Move down` |
| Folders | `Add folder`, `Remove folder`, `Include subfolders` (switch), `Rescan` |
| Settings switches | `Skip silence`, `Equalizer enabled`, `Bass boost`, `Pure black`, `Dynamic color` |
| EQ | `Preset`, sliders `Band <freq> Hz`, `Bass boost strength` |
| Theme | `Follow system`, `Light`, `Dark`, accents `Accent Purple|Blue|Green|Orange|Pink` |
| Widget buttons | `Widget previous`, `Widget play` / `Widget pause`, `Widget next` |
| Tabs (text) | `Folders`, `Artists`, `Albums`, `Playlists` |
| Dialog buttons (text) | `Create`, `Rename`, `Delete`, `Cancel` |

Switches: put the description on the whole row via `Modifier.toggleable(..., role = Role.Switch)` + `semantics
{ contentDescription = … }` so one tap target toggles. Developer copies this table into `handoff-dev.md` with any
additions.
