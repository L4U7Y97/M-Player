# Developer handoff

## Round 1

Scope: phases 1–2 of `docs/tasks.md` (scaffold, data/scan/db) plus **all** pure JVM-testable classes from
spec §2 (`*`), so the tester can start now. No UI beyond a placeholder, no playback, no tests written.

### Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat :app:assembleDebug --console=plain        # APK: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:testDebugUnitTest --console=plain    # once the tester adds app/src/test
```
- `local.properties`: `sdk.dir=C:/Users/lpujol/AppData/Local/Android/Sdk` (forward slashes).
- Wrapper uses `gradle-8.14.3-all.zip` (the cached distribution) instead of the spec's `-bin` (would need a download).
- Test deps already wired in `app/build.gradle.kts`: `junit:junit:4.13.2`, `kotlinx-coroutines-test:1.10.2`,
  plus `testOptions.unitTests.isReturnDefaultValues = true`. `app/src/test/` is empty — tester owns it.
- INTERNET check: see "Verification" below.

### What exists (`app/src/main/java/dev/localmusic/`)

| File | Notes |
|---|---|
| `LocalMusicApp.kt` | Application; `container: AppContainer`; extension `Context.appContainer` |
| `AppContainer.kt` | `appScope`, `database`, `tagReader`, `folderScanner`, `libraryRepository` (lazy) |
| `MainActivity.kt` | Placeholder: centered text "Local Music" (Material3, follows system dark) |
| `data/db/Entities.kt` | `FolderEntity`, `TrackEntity`, `PlaylistEntity`, `PlaylistEntryEntity` (spec §3), projections `FolderGroup(path,name,trackCount)`, `ArtistGroup(name,trackCount)`, `AlbumGroup(album,artist,trackCount)`, `PlaylistWithCount`, `TrackEntity.toTrack()` |
| `data/db/Daos.kt` | `FolderDao`, `TrackDao` (groupings + per-key lists as Flows, `missing = 0`), `PlaylistDao` (CRUD + `replaceEntries` transaction) |
| `data/db/LocalMusicDatabase.kt` | Room v1, `exportSchema = true` → `app/schemas/`; `LocalMusicDatabase.build(ctx)` |
| `data/scan/ScanRules.kt` | pure — see API |
| `data/scan/TagFallbacks.kt` | pure — `RawTags`, `ResolvedTags`, `TagFallbacks` |
| `data/scan/TagReader.kt` | MediaMetadataRetriever, `release()` in finally, any failure → `RawTags()` |
| `data/scan/FolderScanner.kt` | DocumentsContract BFS on IO, 1 query/dir, incremental reuse by (lastModified,size), per-file failures skipped, root failure → `FolderAccessLostException` |
| `data/repo/LibraryRepository.kt` | `ScanState`, folders/groupings/track flows, `addFolder`, `removeFolder`, `setIncludeSubfolders`, `rescanAll`, `rescanAllAsync`, `accessLost: StateFlow<Set<Long>>`, `tracksByIds` |
| `domain/Track.kt` | pure UI model, implements `SortableTrack` |
| `domain/SortSpec.kt`, `domain/TrackSorter.kt` | pure |
| `domain/RepeatCycler.kt`, `domain/QueueModel.kt`, `domain/PlaylistOrdering.kt` | pure |
| `settings/AppSettings.kt`, `settings/EqSettings.kt`, `settings/SettingsMapper.kt` | pure |

Manifest: label "Local Music", POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK;
INTERNET and ACCESS_NETWORK_STATE with `tools:node="remove"`. No service/widget entries yet.

### Public API of the pure classes (no android.* imports — JUnit 4 on the JVM)

#### `dev.localmusic.data.scan.ScanRules` (object)
```kotlin
val SUPPORTED_EXTENSIONS: Set<String>          // mp3, m4a, aac, flac, ogg, oga, opus, wav (lower-case)
fun isHidden(name: String): Boolean             // name.startsWith(".")
fun extensionOf(name: String): String?          // lower-cased text after last '.', null if no dot or trailing dot
fun isAudioFile(name: String): Boolean          // false for blank, hidden (".x.mp3"), unsupported; case-insensitive ext
fun shouldDescend(depth: Int, includeSubfolders: Boolean): Boolean // depth <= 0 → true; depth >= 1 → includeSubfolders
fun shouldVisitDirectory(name: String): Boolean // false for blank or names starting with '.'
fun childPath(parentPath: String, name: String): String // "Music/Rock" + "X" → "Music/Rock/X"; trailing '/' dropped; blank parent → name
```

#### `dev.localmusic.data.scan.TagFallbacks` (object) + data classes
```kotlin
data class RawTags(title: String? = null, artist: String? = null, albumArtist: String? = null, album: String? = null,
                   trackNumber: String? = null, discNumber: String? = null, durationMs: String? = null, year: String? = null)
data class ResolvedTags(title: String, artist: String, album: String, albumArtist: String?,
                        trackNumber: Int?, discNumber: Int?, durationMs: Long, year: Int?)

const val UNKNOWN_ARTIST = "Unknown artist"; const val UNKNOWN_ALBUM = "Unknown album"
fun resolve(raw: RawTags, fileName: String, parentName: String): ResolvedTags
fun clean(value: String?): String?            // trim; blank → null
fun titleFromFileName(fileName: String): String // strip last extension ("a.b.mp3" → "a.b"); no dot → name; ".mp3" → ".mp3"
fun parseNumber(value: String?): Int?         // "3", "3/12", " 03 " → 3; "abc", "", "0", "-1" → null
fun parseYear(value: String?): Int?           // first run of 4 digits anywhere ("2003-05-01" → 2003); none/"0000" → null
fun parseDuration(value: String?): Long       // decimal ms; null/garbage/negative → 0
```
`resolve` rules: every string field trimmed, blank → null, then: title → `titleFromFileName(fileName)`;
artist → albumArtist → `"Unknown artist"`; album → `parentName` (trimmed) → `"Unknown album"` if that is blank too;
albumArtist stays nullable (blank → null).

#### `dev.localmusic.domain` — sorting
```kotlin
enum class SortField { TRACK_NUMBER, TITLE, FILE_NAME, DATE_ADDED }
data class SortSpec(field: SortField = TRACK_NUMBER, ascending: Boolean = true) { companion object { val DEFAULT } }
interface SortableTrack { val title: String; val fileName: String; val trackNumber: Int?; val discNumber: Int?; val dateAdded: Long }

object TrackSorter {
    val FILE_NAME_TIE_BREAK: Comparator<SortableTrack>   // case-insensitive, then exact String compare
    fun <T : SortableTrack> sort(tracks: List<T>, spec: SortSpec): List<T>   // stable
    fun comparator(spec: SortSpec): Comparator<SortableTrack>
}
```
- TRACK_NUMBER: key `(discNumber ?: 1, trackNumber)`; null trackNumber always last in both directions (among
  themselves ordered by tie-break only, disc ignored). TITLE/FILE_NAME: `String.CASE_INSENSITIVE_ORDER`. DATE_ADDED: Long.
- Descending reverses the primary key only; tie-break is always fileName ascending.
- `Track` (below) implements `SortableTrack`; tests may also use their own small data class implementing it.

```kotlin
data class Track(id: String, uri: String, folderId: Long, parentPath: String, parentName: String, fileName: String,
                 title: String, artist: String, album: String, albumArtist: String?, trackNumber: Int?, discNumber: Int?,
                 durationMs: Long, year: Int?, dateAdded: Long, missing: Boolean = false) : SortableTrack
// val albumGroupArtist: String = albumArtist ?: artist
```

#### `dev.localmusic.domain.RepeatCycler` (object) + `enum class RepeatMode { OFF, ALL, ONE }`
```kotlin
const val PLAYER_REPEAT_MODE_OFF = 0; PLAYER_REPEAT_MODE_ONE = 1; PLAYER_REPEAT_MODE_ALL = 2   // = Media3 values
fun next(mode: RepeatMode): RepeatMode        // OFF → ALL → ONE → OFF
fun fromPlayerMode(playerMode: Int): RepeatMode // unknown int → OFF
fun toPlayerMode(mode: RepeatMode): Int
fun nextPlayerMode(playerMode: Int): Int      // 0 → 2 → 1 → 0; unknown → 2
fun label(mode: RepeatMode): String           // "Repeat off" / "Repeat all" / "Repeat one"
```

#### `dev.localmusic.domain.QueueModel` (object) + `data class QueueState<T>(items: List<T>, currentIndex: Int)`
```kotlin
fun <T> move(items: List<T>, currentIndex: Int, from: Int, to: Int): QueueState<T>
fun <T> remove(items: List<T>, currentIndex: Int, index: Int): QueueState<T>
fun <T> append(items: List<T>, currentIndex: Int, newItems: List<T>): QueueState<T>
fun playOrder(count: Int, shuffledOrder: List<Int>? = null): List<Int>
fun upNext(count: Int, current: Int, shuffledOrder: List<Int>? = null): List<Int>
```
- `move` (= `Player.moveMediaItem`): item at `from` ends at `to`. current: moved item was current → `to`;
  from < current ≤ to → current − 1; to ≤ current < from → current + 1; else unchanged. `from`/`to` out of
  `items.indices` or equal → returns input unchanged.
- `remove` (= `Player.removeMediaItem`, repeat off): index < current → current − 1; index > current → same;
  index == current → same index (next item) or, if it was the last item, **0**; result empty → **-1**.
  Out of range → unchanged.
- `append`: items + newItems; current unchanged, except empty queue or current < 0 → 0. Empty newItems → unchanged.
- `playOrder`: `0 until count` or `shuffledOrder` if it is a permutation of `0 until count` (else linear);
  count ≤ 0 → empty. `upNext`: indices after `current` in that order (excluded current); current not present → whole order.
- **Deviation from spec §6:** spec wrote `playOrder(count, current, shuffledOrder?)`; split into `playOrder` (full
  order) + `upNext` (after current) because "current" has no role in a full play order.

#### `dev.localmusic.domain.PlaylistOrdering` (object) + `data class PlaylistItem(entryId: Long = 0, trackId: String, position: Int)`
```kotlin
fun renumber(entries: List<PlaylistItem>): List<PlaylistItem>   // position = list index
fun normalize(entries: List<PlaylistItem>): List<PlaylistItem>  // stable sort by position, then renumber
fun move(entries: List<PlaylistItem>, from: Int, to: Int): List<PlaylistItem>
fun remove(entries: List<PlaylistItem>, index: Int): List<PlaylistItem>
fun append(entries: List<PlaylistItem>, trackIds: List<String>): List<PlaylistItem> // new items have entryId 0
```
All ops use the **list order** of the input (not the `position` field) and always return positions 0..n-1,
including no-ops (out-of-range index → input order, renumbered). Duplicate trackIds allowed.

#### `dev.localmusic.settings` — settings model + `SettingsMapper` (object)
```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Accent(val label: String) { PURPLE("Purple"), BLUE("Blue"), GREEN("Green"), ORANGE("Orange"), PINK("Pink") }
data class AppSettings(themeMode = SYSTEM, pureBlack = false, accent = PURPLE, dynamicColor = true,
                       skipSilence = false, sort = SortSpec.DEFAULT, eq = EqSettings())
data class EqSettings(enabled = false, presetIndex = -1, bandLevelsMb: List<Int> = emptyList(),
                      bassBoostEnabled = false, bassBoostStrength = 0)
// EqSettings.CUSTOM_PRESET = -1, MIN_STRENGTH = 0, MAX_STRENGTH = 1000; empty bandLevelsMb = flat

object SettingsMapper {
    fun enumToString(value: Enum<*>): String                                  // value.name
    inline fun <reified E : Enum<E>> enumFromString(stored: String?, default: E): E // trimmed, case-insensitive; null/blank/unknown → default
    fun themeModeFromString(stored: String?): ThemeMode                       // default SYSTEM
    fun accentFromString(stored: String?): Accent                             // default PURPLE
    fun sortFieldFromString(stored: String?): SortField                       // default TRACK_NUMBER
    fun bandLevelsToCsv(levels: List<Int>): String                            // [-300,0,150] → "-300,0,150"; [] → ""
    fun bandLevelsFromCsv(csv: String?, minMb: Int, maxMb: Int, bandCount: Int? = null): List<Int>
    fun clampBandLevel(levelMb: Int, minMb: Int, maxMb: Int): Int             // bounds swapped if reversed
    fun clampStrength(strength: Int): Int                                     // 0..1000
    fun sanitizePresetIndex(index: Int): Int                                  // < -1 → -1
}
```
`bandLevelsFromCsv`: split on ',', each entry trimmed, non-int → 0, clamp to [min,max]; null/blank csv → [];
`"-300,x,99999"` with ±1500 → `[-300, 0, 1500]`; with `bandCount` non-null → truncated or padded with
`clampBandLevel(0, min, max)` to exactly that size.

### Non-pure data layer notes (for later rounds / QA)
- Track id `"<authority>|<documentId>"`; overlapping folders dedupe; on rescan the first folder (by id) that sees
  a track owns it.
- `rescanAll()`: holds a Mutex; upserts seen tracks with missing = 0; marks unseen tracks of successfully scanned
  folders missing; folders without persisted permission (or root listing failure) → all their tracks missing and
  id in `accessLost`.
- `removeFolder`: releases permission, deletes unreferenced tracks, marks referenced ones missing, deletes the
  row, rescans if folders remain.
- `addFolder(treeUri)`: takes persistable read permission, inserts (includeSubfolders = true), rescans; returns id.

### Verification (2026-10-26)
- `.\gradlew.bat :app:assembleDebug --console=plain` → BUILD SUCCESSFUL (first run ~10 min incl. downloads), no
  Kotlin warnings. Room schema exported to `app/schemas/dev.localmusic.data.db.LocalMusicDatabase/1.json`.
- `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`: 0 matches for
  `INTERNET` / `ACCESS_NETWORK_STATE`.
- `aapt.exe dump permissions app-debug.apk` → POST_NOTIFICATIONS, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, `dev.localmusic.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx
  core, harmless). No INTERNET.
- Manifest merger warns that INTERNET's `tools:node="remove"` has nothing to remove. That's expected: the remove is a guard.

### Incomplete / not done this round
- T2 AC "installs and launches on emulator" not run (no emulator step this round); build + manifest check only.
- T3: deps wired, but the trivial test is left to the tester (per orchestrator: tester owns `src/test`).
- T7/T8 ACs (nested scan, corrupt file, missing marking, permission release) are implemented but unverified on a
  device; no UI exists yet to add a folder (Phase 4, T20).
- Spec §11 QA label table: not applicable yet (no UI); will be copied here when screens exist.

## Round 2

Scope: Phases 3–5 of `docs/tasks.md` (playback service, all UI screens + navigation + mini player, playlists).
Phase 6 (EQ, theme screen), 7 (widget) and 8 (resumption) are left for the next round; hooks listed below.
Build unchanged: `.\gradlew.bat :app:assembleDebug --console=plain` → `app\build\outputs\apk\debug\app-debug.apk`.
No new dependencies, no version changes. Merged manifest still has no INTERNET / ACCESS_NETWORK_STATE.
No public signature of the round-1 pure classes changed. `app/src/test` not touched.

### New / changed files (`app/src/main/java/dev/localmusic/`)

| File | What |
|---|---|
| `playback/PlaybackService.kt` | `MediaSessionService`. ExoPlayer: music audio attributes + audio focus, becoming-noisy, `WAKE_MODE_LOCAL`, `audioSessionId` from AppContainer. `MediaSession` with session activity → MainActivity. Callback `onAddMediaItems` restores URIs. `onTaskRemoved` stops the service if paused or empty. Collects `skipSilence` (Phase 6 hook). Logs `playbackState = N` and `transition -> <mediaId> (reason R)` under tag `PlaybackService` |
| `playback/MediaItemMapper.kt` | `toMediaItem(track)` (mediaId = track id, uri + `requestMetadata.mediaUri`, title/artist/album/albumArtist/track/disc), `restoreUri(item)` |
| `playback/PlayerConnection.kt` | `MediaController` wrapper (process singleton). `connect()`/`disconnect()` are ref-counted from `MainActivity.onStart/onStop`. `state: StateFlow<PlayerUiState>`: meta, isPlaying, position (polled every 500 ms), duration, shuffle, `RepeatMode`, `queue: List<QueueItem>` in **play order**, `currentIndex` (timeline index). Commands: `playTracks(list, start, shuffle)` (one `setMediaItems` + `prepare` + `play`; logs `prepare() with N items` under tag `PlayerConnection`), `togglePlayPause`, `next`, `previous`, `seekTo`, `toggleShuffle`, `cycleRepeat` (via `RepeatCycler`), `playQueueIndex`, `moveQueueItem`, `removeQueueItem`, `addToQueue` (an empty queue starts playing instead) |
| `settings/SettingsRepository.kt` | DataStore `settings.preferences_pb`. `settings: Flow<AppSettings>`, `state: StateFlow`, setters for sort, skip silence, theme mode, pure black, accent, dynamic color, and `updateEq{}`. Written now because the sort has to persist; Phase 6 reuses it |
| `data/repo/PlaylistRepository.kt` | create/rename/delete, `addTracks`, `move`, `remove` (all through `PlaylistOrdering` + `replaceEntries`), `entries(id): Flow<List<PlaylistTrack>>` (`missing` = track row gone or `missing = 1`) |
| `data/db/Daos.kt`, `Entities.kt` | **Added** `PlaylistDao.observeEntriesWithTracks` + `PlaylistEntryWithTrack` (@Relation). No schema change (still v1) |
| `AppContainer.kt` | Adds `playlistRepository`, `settingsRepository`, `playerConnection`, `audioSessionId` (`AudioManager.generateAudioSessionId()`) |
| `MainActivity.kt` | `LocalMusicTheme(settings) { AppNavHost() }`; connects PlayerConnection; asks POST_NOTIFICATIONS once on API 33+ (flag in SharedPreferences `app_state`) |
| `ui/theme/Theme.kt` | `LocalMusicTheme(settings)`: mode or system dark, dynamic color on 31+, 5 accent schemes, pure black. Already works; only the Theme *screen* is missing |
| `ui/nav/Routes.kt`, `ui/nav/AppNavHost.kt` | String routes, args `Uri.encode`d; root `testTagsAsResourceId = true` |
| `ui/components/` | `Common.kt` (`ActionIcon`, `BackButton`, `SwitchRow`, `EmptyState`, `formatDuration`), `MiniPlayer.kt`, `TrackRow.kt`, `Dialogs.kt` (`NameDialog`, `ConfirmDialog`, `AddToPlaylistDialog`) |
| `ui/library/LibraryScreen.kt` | Tabs + Playlists tab (+ `LibraryViewModel`) |
| `ui/detail/TrackListScreen.kt` | Folder/artist/album lists, `SortMenu`, `PlayButtonsRow` (+ `TrackListViewModel`) |
| `ui/playlists/PlaylistDetailScreen.kt` | Playlist detail (+ VM) |
| `ui/nowplaying/NowPlayingScreen.kt`, `ui/queue/QueueScreen.kt` | Read `PlayerConnection.state` directly |
| `ui/settings/SettingsScreen.kt`, `FoldersScreen.kt` | Settings root, Music folders manager, `ComingSoonScreen` placeholder |

### Service declaration (manifest)
```xml
<service android:name=".playback.PlaybackService" android:exported="true"
    android:foregroundServiceType="mediaPlayback">
  <intent-filter>
    <action android:name="androidx.media3.session.MediaSessionService" />
    <action android:name="android.media.browse.MediaBrowserService" />
  </intent-filter>
</service>
```
No `MediaButtonReceiver` yet (only together with T30 resumption, spec §5). The notification and lock-screen controls come
from Media3's default `DefaultMediaNotificationProvider` (channel `default_channel_id`, notification id 1001).

### Screens and how to reach them

| Route | Screen | How to get there |
|---|---|---|
| `library` | TopAppBar "Local Music", action `Settings`; tabs `Folders` / `Artists` / `Albums` / `Playlists`; empty state "No music folders yet" + button `Add folder` (opens the SAF picker directly); MiniPlayer at the bottom | start destination |
| `folder/{path}` | Track list for a folder (title = last path segment, subtitle = path) | tap a `folder_row` |
| `artist/{name}` | Track list by artist | Artists tab → tap `artist_row` |
| `album/{album}/{artist}` | Track list for an album (artist = albumArtist ?: artist) | Albums tab → tap `album_row` |
| `playlist/{id}` | Playlist detail | Playlists tab → tap `playlist_row` |
| `now_playing` | Now Playing | tap the MiniPlayer body `Open now playing` |
| `queue` | Queue | Now Playing → `Queue` |
| `settings` | Rows "Music folders", "Equalizer", "Theme", switch `Skip silence` | Library → `Settings` |
| `settings/folders` | Music folders manager | Settings → "Music folders" |
| `settings/equalizer`, `settings/theme` | Placeholder "… settings are coming in the next build." (Phase 6) | Settings → "Equalizer" / "Theme" |

Every non-root screen has a `Back` navigate-up icon; system back works too. The MiniPlayer shows on library, track
list and playlist detail screens once the player has an item.

Track list screens (folder/artist/album): the first list item holds the buttons `Play all` / `Shuffle all` (text). Top
action `Sort` opens a menu with "Track number", "Title", "File name", "Date added", "Ascending", "Descending"; the
current choices have a check icon (desc "Selected"). The sort is global and persisted. Rows (`track_row`): title +
"artist · m:ss"; tap = play the sorted list from that row; `More options` → "Add to queue", "Add to playlist".

Playlists tab: extended FAB with text `New playlist` → dialog "New playlist" with field label "Playlist name" (testTag
`playlist_name_field`) and buttons `Create` / `Cancel`. Row (`playlist_row`) → `More options` → "Rename playlist"
(dialog, buttons `Rename` / `Cancel`) or "Delete playlist" (confirm dialog, buttons `Delete` / `Cancel`).
"Add to playlist" dialog (from track rows and Now Playing): title "Add to playlist", first row "New playlist" (→ name
dialog with `Create`), then one row per playlist (testTag `playlist_choice`), button `Cancel`.
Playlist detail: `Play all` / `Shuffle all`; rows `track_row` with `Move up`, `Move down`, `Remove from playlist`, and
`More options` → "Add to queue". Missing rows are greyed with subtitle text "Missing", not tappable, and skipped when playing.

Now Playing: title, artist, album text; `Seek bar` slider; elapsed / total time text; `Shuffle` (IconToggleButton; the
node with that desc is checkable, `checked=true/false`), `Previous`, `Play`/`Pause`, `Next`,
`Repeat off`/`Repeat all`/`Repeat one`, `Queue`, `Add to playlist`. Empty state: "Nothing is playing".

Queue: rows `queue_row` in play order. The current row is highlighted, its subtitle is "Now playing · <artist>", and its
row node is `selected` (uiautomator also shows it as `checked=true`). Tap a row = jump to it. `Move up` / `Move down`
are disabled while shuffle is on (the top bar then says "Shuffle is on: reordering disabled") and at the list ends.
`Remove from queue` always works.

Music folders: top action `Rescan` (disabled while scanning); progress text "Scanning… N files" (testTag
`scan_progress`; after a scan it reads "Last scan: N files"); extended FAB text `Add folder`. Per folder (testTag
`music_folder_row`): name, "Access lost" text when the permission is gone, `Remove folder` (no confirm dialog), switch
row `Include subfolders`.

### QA label table (spec §11 + additions), as implemented

All icon buttons carry the description on the **clickable node itself** (`ActionIcon`), so uiautomator shows
content-desc together with `clickable`/`enabled`. Switch rows are a single toggleable node (role Switch) with the
description and `checked`.

| Element | contentDescription / text | Notes |
|---|---|---|
| Play / pause (mini player, Now Playing) | `Play` when paused, `Pause` when playing | |
| Next / previous | `Next` (mini player + Now Playing), `Previous` (Now Playing) | |
| Shuffle toggle | `Shuffle` | checkable, checked = shuffle on |
| Repeat | `Repeat off` / `Repeat all` / `Repeat one` | a tap cycles OFF→ALL→ONE→OFF |
| Queue button / seek bar | `Queue`, `Seek bar` | tap or swipe inside the seek bar bounds |
| Mini player body | `Open now playing` | |
| Navigate up | `Back` | |
| Library settings | `Settings` | |
| Sort menu / row overflow | `Sort`, `More options` | |
| List buttons (text) | `Play all`, `Shuffle all` | |
| Queue row | `Move up`, `Move down`, `Remove from queue` | |
| Playlist | `New playlist` (FAB text + dialog row), `Rename playlist`, `Delete playlist` (menu item texts), `Remove from playlist`, `Add to playlist` (desc on Now Playing, menu text on rows), `Move up`, `Move down` | |
| Folders | `Add folder` (text), `Remove folder`, `Include subfolders` (switch row), `Rescan` | |
| Settings switches | `Skip silence` | the others come with Phase 6 |
| Tabs (text) | `Folders`, `Artists`, `Albums`, `Playlists` | |
| Dialog buttons (text) | `Create`, `Rename`, `Delete`, `Cancel` | |
| testTags → resource-id | `folder_row`, `artist_row`, `album_row`, `playlist_row`, `track_row`, `queue_row`, plus `playlist_choice`, `music_folder_row`, `playlist_name_field`, `scan_progress` | dialogs re-enable `testTagsAsResourceId` inside their own window |
| Not yet (Phase 6/7) | `Equalizer enabled`, `Bass boost`, `Pure black`, `Dynamic color`, `Preset`, `Band <freq> Hz`, `Bass boost strength`, `Follow system`/`Light`/`Dark`, `Accent …`, widget buttons | |

### Phase 6–8 hooks already in place
- `SettingsRepository` covers every `AppSettings` field, including `updateEq{}`. `LocalMusicTheme` already applies
  mode / pure black / accent / dynamic color live, so Phase 6 only needs the Theme and Equalizer screens (the routes
  exist and show a placeholder) plus `AudioEffectsController` (use `container.audioSessionId`; it is already set on the player).
- Skip silence: the switch on the Settings root is persisted; the service sets `player.skipSilenceEnabled` and logs
  `skipSilenceEnabled = <bool>`. T27 is left unticked for Phase 6 verification.
- POST_NOTIFICATIONS is asked once at first launch on 33+. T29 is coded but unticked: QA verifies the prompt.
- Widget (Phase 7): add a `Player.Listener` in `PlaybackService.onCreate` next to `LoggingListener`.

### Verification (round 2, 2026-10-26)
- `.\gradlew.bat :app:assembleDebug --console=plain` → BUILD SUCCESSFUL, no Kotlin warnings. Merged manifest and
  `aapt dump permissions` show no INTERNET / ACCESS_NETWORK_STATE.
- Smoke test on AVD `test` (API 35) with 4 generated WAVs pushed to `/sdcard/Music/TestMusic` (+ `Nested/`):
  empty state → `Add folder` → SAF pick + ALLOW → Folders tab listed both folders with counts → folder detail →
  `Play all` → mini player (`Open now playing` / `Pause` / `Next`) → one `prepare()` and AUTO transitions through all 3
  tracks → foreground media notification with actions → HOME + `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_MEDIA_NEXT`
  controlled playback → Now Playing: Shuffle + Repeat toggled (desc changed to `Repeat all`) → Queue showed the
  shuffled play order, and the linear order with shuffle off.
- After that run I moved every icon description onto its clickable node (`ActionIcon`) and rebuilt (green). The
  re-run on the emulator failed for environmental reasons: the host was down to about 400 MB of free RAM, system-wide
  ANRs, and system_server restarted. So the **final APK was not re-smoke-tested**. **The emulator `test` (emulator-5554)
  is still running**, the test WAVs are still on it, and the TestMusic folder grant is still in the app's data.

### Known gaps / notes for QA
- Not verified on a device yet: lock screen / screen-off playback (T9), sort reorder + persistence (T16), seeking
  (T18), queue reorder/remove while playing (T19), Include-subfolders toggle + rescan (T20), playlists end to end (T22/T23).
- "Shuffle all" = random start index + `shuffleModeEnabled`. Media3's shuffle order is random over the whole list,
  so with repeat off some tracks can come *before* the start track in play order and are not played. The Queue screen
  shows them above the current row.
- When the current queue item is the last one and is removed, ExoPlayer moves to item 0 and ENDs. The Queue then
  highlights item 0 as current while paused (matches `QueueModel.remove`; see handoff-test note 3).
- `Remove folder` has no confirmation dialog (the spec doesn't ask for one).
- `addToQueue` on an empty queue starts playback of those tracks.
- PlayerConnection is connected only while MainActivity is started. Commands issued while disconnected are dropped
  (with a log line), which can't happen from the UI.
- First launch on a cold, overloaded emulator can show "isn't responding" before the first frame. Every system app
  did the same at that moment, so it is not app-specific. Library/DB work runs on IO or the app scope.
- `docs/agents/bugs.md` did not exist at the end of this round, so there were no open bugs to fix.

## Round 3

Scope: Phases 6–8 (equalizer, themes, skip silence, widget, polish) plus fixes for the issues the tester and orchestrator raised.
**No emulator was started this round** (host low on RAM). Everything below is verified by build and unit tests only; on-device
checks are QA's (checklist at the end). `docs/agents/bugs.md` still does not exist, so there were no `[open]` bugs to fix.

### Build / verify
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest --console=plain   # green: 146 tests, 0 failures, no Kotlin warnings
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
`aapt dump permissions app-debug.apk` lists POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK,
**MODIFY_AUDIO_SETTINGS** (new: a normal install-time permission that some audio-effect stacks need) and androidx's
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. It lists **no INTERNET / ACCESS_NETWORK_STATE**, and the merged manifest has 0 matches.
No dependency or version changes. If `clean` fails with "Unable to delete … classes.jar", run `.\gradlew.bat --stop` first.

### Fixes (orchestrator list B)
| # | Fix | Where |
|---|---|---|
| 1 | "Shuffle all" plays every track exactly once. `playTracks(shuffle=true)` sets the items with shuffle off and then turns shuffle on. Every time shuffle turns on (this path or the Now Playing toggle), the service's `ShuffleFromCurrentListener` installs `DefaultShuffleOrder(QueueModel.shuffledOrderStartingWith(count, current))`, so the current item is first in play order. Logcat: `PlaybackService: shuffle order starts at <i> (<n> items)` | `PlayerConnection.playTracks`, `PlaybackService`, `QueueModel.shuffledOrderStartingWith` (pure, tested) |
| 2 | TITLE / FILE_NAME sorts and the fileName tie-break use natural, case-insensitive order: "Track 2" < "Track 10". `01` and `1` compare equal at that level and then fall to a deterministic tie-break | new pure `domain/NaturalOrder.kt`, `TrackSorter` |
| 3 | A **subfolder** containing `.nomedia` (matched case-insensitively) is skipped together with everything below it. The picked root is never skipped, because the user chose it explicitly. This is a design decision and one line in `ScanRules.shouldSkipDirectory` changes it. Logcat: `FolderScanner: Skipping <path>: contains .nomedia` | `ScanRules.hasNoMediaMarker` / `shouldSkipDirectory`, `FolderScanner` |
| 4 | New `PlayerUiState.ended` (= STATE_ENDED). While it is set, Queue highlights no row and marks none `selected`. So when the current item is removed while it is last (ExoPlayer parks on item 0 and ENDs), item 0 is no longer shown as "Now playing" | `PlayerConnection`, `QueueScreen` |

Tests I added (as the orchestrator allowed): `TrackSorterTest` +5 (natural order), `ScanRulesTest` +3 (`.nomedia`), `QueueModelTest`
+2 (shuffled order). No existing test needed changes.

### New / changed files
| File | What |
|---|---|
| `playback/AudioEffectsController.kt` (`container.audioEffects`) | Creates `Equalizer(0, sessionId)` + `BassBoost(0, sessionId)` on `container.audioSessionId` lazily, in try/catch, and exposes `EffectsState(checked, equalizerAvailable, bassBoostAvailable, bassStrengthSupported, caps)`. `start()` runs from service `onCreate` and from the EQ screen. It collects `settings.eq` and applies it: a preset ≥ 0 → `usePreset`, then reads the band levels back and saves them (so the sliders move); custom → `setBandLevel` per band (clamped); `enabled`; bass `setStrength` + `enabled`. On an effect error the effects are released and the next `start()` re-creates them. Logcat tag `AudioEffects` (`effects created on session …`, `applied EqSettings(…)`) |
| `ui/settings/EqualizerScreen.kt`, `ui/settings/ThemeScreen.kt` | Replace the round-2 placeholders (`ComingSoonScreen` removed) |
| `widget/NowPlayingWidgetProvider.kt`, `WidgetRenderer.kt`, `WidgetStateStore.kt` | RemoteViews widget |
| `res/layout/widget_now_playing.xml`, `res/xml/now_playing_widget_info.xml`, `res/drawable/ic_widget_*.xml`, `widget_background.xml`, `res/values-night/themes.xml` | Widget resources; a dark window before the first Compose frame when the system is dark |
| `PlaybackService` | Shuffle-order listener, widget-state listener, `audioEffects.start()`, widget reset in `onDestroy` |
| `MainActivity`, `ui/theme/Theme.kt` | `enableEdgeToEdge` follows the **app's** dark flag, so system-bar icons stay legible when Dark/Light is forced. Pure black also blacks out `surfaceContainerLow/Lowest`. New `isAppInDarkTheme(settings)` |
| `LocalMusicApp` | On process start, resets a stale "active" widget state (the service re-publishes it when it starts) |
| Manifest | `MODIFY_AUDIO_SETTINGS`; `<receiver .widget.NowPlayingWidgetProvider exported=false>` with `APPWIDGET_UPDATE` + provider meta-data |

### How to reach the new screens
Library → `Settings` (top-bar icon) →
- row **"Equalizer"** → `settings/equalizer`
- row **"Theme"** → `settings/theme`
- switch row **`Skip silence`** (on the Settings root itself; it already existed)

### Equalizer screen (`settings/equalizer`)
- Switch row `Equalizer enabled` (role Switch, `checked`).
- Row `Preset`. It shows the current preset name under the label ("Custom" when custom). Tapping it opens a dropdown with text items `Custom`
  plus the device presets (AOSP/emulator: Normal, Classical, Dance, Flat, Folk, Heavy Metal, Hip Hop, Jazz, Pop, Rock). The current item has a check icon (desc `Selected`).
- One slider per band, desc `Band <freq> Hz`, where freq is the center frequency in whole Hz. AOSP gives `Band 60 Hz`, `Band 230 Hz`,
  `Band 910 Hz`, `Band 3600 Hz`, `Band 14000 Hz`. Each slider has a text label `<freq> Hz` and a `+x.x dB` value. Dragging previews the sound live; releasing persists the value and switches Preset to Custom.
- Switch row `Bass boost`, then slider `Bass boost strength` (0..1000, text "Bass boost strength: N%"). The slider only appears if the device supports setting strength.
- On a device without effects, the text **"Equalizer not available on this device"** replaces the whole screen (when neither effect exists) or just the EQ part.
  "Bass boost not available on this device" works the same way.

### Theme screen (`settings/theme`)
- Radio rows (role RadioButton, `selected`): `Follow system`, `Light`, `Dark`.
- Switch rows: `Pure black` (black background, dark mode only) and `Dynamic color` (API 31+ only; default **on**).
- Accent swatches (role RadioButton, `selected`): `Accent Purple`, `Accent Blue`, `Accent Green`, `Accent Orange`, `Accent Pink`.
  An accent only applies with Dynamic color off, so **picking an accent also turns Dynamic color off**. While Dynamic color is on, no swatch shows as selected.
- Every change applies live (no restart) and persists in DataStore.

### Widget
- Provider: **`dev.localmusic/.widget.NowPlayingWidgetProvider`** (label "Local Music"; target 4×1 cells, min 180×40 dp, resizable both ways).
- It shows title + artist and three ImageButtons: `Widget previous`, `Widget play` / `Widget pause`, `Widget next` (contentDescription on each).
  Tapping the text or background opens MainActivity. Empty state (service not running or queue empty): "Not playing" / "Tap to open Local Music", and **every button opens the app**.
- Updates: a player listener in the service writes `WidgetStateStore` (SharedPreferences `widget_state`) and pushes new RemoteViews on every
  relevant change (track, play/pause, metadata, queue), but only when what the widget shows actually changed. Service `onDestroy` → empty state.
- **Deviation from spec §8:** the buttons send explicit broadcasts (`dev.localmusic.widget.PREVIOUS|PLAY_PAUSE|NEXT`) to the provider. The provider
  connects a short-lived `MediaController` and sends `seekToPrevious` / play-pause / `seekToNext`. Reason: a `getForegroundService` start that
  does not start playback (e.g. Next while paused) must call `startForeground` within seconds or the app crashes. Logcat tag `NowPlayingWidget` (`widget command … sent`).
- Adding it on the emulator: long-press an empty spot on the home screen → Widgets → "Local Music" → drag it onto the home screen
  (Pixel launcher). No generic adb command places a widget, so use the long-press path. To confirm the provider is registered:
  `adb shell dumpsys appwidget | findstr localmusic`. After placing it, a widget id appears under the provider.

### QA label table, round-3 additions (the round-2 table is still valid)
| Element | contentDescription / text |
|---|---|
| Settings root | rows "Music folders", "Equalizer", "Theme" (text), switch `Skip silence` |
| EQ | `Equalizer enabled` (switch), `Preset` (row) → menu texts `Custom` + device preset names, sliders `Band <freq> Hz`, `Bass boost` (switch), `Bass boost strength` (slider), text "Equalizer not available on this device" |
| Theme | `Follow system`, `Light`, `Dark` (radio rows), `Pure black`, `Dynamic color` (switches; Dynamic color API 31+ only), `Accent Purple` / `Accent Blue` / `Accent Green` / `Accent Orange` / `Accent Pink` |
| Widget | `Widget previous`, `Widget play` / `Widget pause`, `Widget next`; texts "Not playing", "Tap to open Local Music" |
| Queue | no row is `selected` or highlighted once playback has ENDED |

### Known gaps
- Nothing from this round was run on a device. T25–T29 and T31 are coded but stay unticked until QA confirms them.
- T30 (queue persistence, resumption, MediaButtonReceiver) is not done; it is optional.
- With shuffle on, `Add to queue` inserts at a random play-order position (Media3 default), which may be before the current item.
- When a widget button starts playback from the background, it goes through a controller binding, and on API 31+ Media3 then promotes the service to the foreground.
  The widget-interaction exemption should allow this. If it does not, Media3 logs `ForegroundServiceStartNotAllowedException` and nothing crashes. Worth checking in logcat.
- The effects attach to the app's own audio session id before playback starts, which AOSP supports. If an OEM build does not, the screen still works but the sound won't change.

### QA checklist (docs/intent.md success criteria → UI steps)
1. **Pick a folder**: fresh install → `Add folder` → SAF, pick `Music/TestMusic` → "Use this folder" → ALLOW → the Folders tab lists it with a count. Add a `.nomedia` file to a subfolder → Settings → "Music folders" → `Rescan` → that subfolder's tracks disappear.
2. **Browse**: the `Folders` / `Artists` / `Albums` / `Playlists` tabs each list rows (`folder_row` / `artist_row` / `album_row` / `playlist_row`); tapping a row opens its track list.
3. **Play**: `Play all` → the mini player shows `Pause`. `Open now playing` → swipe in the `Seek bar` → elapsed time changes. `Previous` / `Next` / `Pause` work.
4. **Shuffle / repeat**: in a list, `Shuffle all` → Now Playing `Shuffle` is checked=true → `Queue` shows the current row **first** with all other tracks below it. With repeat off, every track plays once and then playback stops. Tapping Repeat cycles `Repeat off` → `Repeat all` → `Repeat one` → `Repeat off`.
5. **Sort**: in a list with files like `Track 2` and `Track 10`, `Sort` → "Title" or "File name" puts 2 before 10; "Descending" reverses the order. Restart the app → the sort is kept.
6. **Queue**: with shuffle off, `Move up` / `Move down` change the order without interrupting playback. `Remove from queue` on the current row advances to the next track. Removing the current row when it is last → no row highlighted, and the play button shows `Play`.
7. **Playlist**: Playlists tab → `New playlist` → enter a name → `Create`. On three tracks, `More options` → "Add to playlist". Open the playlist → `Move up` / `Move down` / `Remove from playlist`. Row `More options` → "Rename playlist" / "Delete playlist". Restart → everything persisted. A deleted file shows as "Missing".
8. **EQ**: Settings → "Equalizer" → turn `Equalizer enabled` on → `Preset` → "Rock" → the `Band … Hz` sliders move. Drag one slider → Preset shows "Custom". Turn `Bass boost` on and drag `Bass boost strength`. Force-stop and reopen → same values, and logcat shows `AudioEffects: applied …` when playback starts. No crash; on an unsupported device the "Equalizer not available on this device" text shows instead.
9. **Themes**: Settings → "Theme" → `Dark` + `Pure black` → background turns black immediately. Then `Light`, then `Follow system`. `Accent Green` → Dynamic color turns off and the colors change live. Turn `Dynamic color` back on (API 31+). Restart → choices kept.
10. **Skip silence**: while playing, turn Settings → `Skip silence` on → logcat shows `PlaybackService: skipSilenceEnabled = true`. Restart → still on.
11. **Widget**: add it (long-press home → Widgets → Local Music). With nothing playing it shows "Not playing", and tapping any button opens the app with no crash. Play a list, press HOME → within about 1 s the widget shows title/artist and `Widget pause`. `Widget next` changes the title. `Widget pause` → the icon and desc become `Widget play`. Tapping the text opens the app.
12. **Lock screen**: while playing, `adb shell input keyevent KEYCODE_POWER` turns the screen off and audio continues. Wake the device → the lock-screen media controls (play/pause/next) work.
13. **Gapless**: an album of consecutive tracks plays with a single `PlayerConnection: prepare()` and AUTO transitions (reason 1).
14. **No crashes**: `adb logcat -b crash` is empty after the whole run. **No INTERNET**: see `aapt dump permissions` above.

## Bug-fix round (2026-10-26) — BUG-1, BUG-2, BUG-3 + cold-start splash polish

Build: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain` → BUILD SUCCESSFUL, 161 unit tests, 0 failures
(new: `ShuffleOrderMathTest` 11, `ToneMathTest` 4). `aapt dump permissions` unchanged, no INTERNET. APK:
`app/build/outputs/apk/debug/app-debug.apk` (15:40). Not installed: QA owns `emulator-5554`, so install with `adb install -r` (data is kept).

| Bug | Change | Files |
|---|---|---|
| BUG-1 | The player's shuffle order is now `AppendingShuffleOrder` (installed in `PlaybackService.onCreate` and by `ShuffleFromCurrentListener` instead of `DefaultShuffleOrder`). `cloneAndInsert` appends new timeline indices to the **end** of the play order and keeps the existing order; `cloneAndRemove`/`cloneAndMove` keep the play order and renumber indices. The index math is pure `domain/ShuffleOrderMath` (insert/remove/move) with unit tests. "Shuffle all" / Shuffle toggle still put the current item first. | `playback/AppendingShuffleOrder.kt` (new), `domain/ShuffleOrderMath.kt` (new), `playback/PlaybackService.kt`, `test/.../domain/ShuffleOrderMathTest.kt` (new) |
| BUG-2 | `ExtendedFloatingActionButton(modifier = Modifier.testTag(...).semantics { contentDescription = ... })`. Labels match spec §11: `New playlist` (testTag `new_playlist_fab`), `Add folder` (testTag `add_folder_fab`). | `ui/library/LibraryScreen.kt`, `ui/settings/FoldersScreen.kt` |
| BUG-3 | `accentScheme()` builds the full scheme. Purple = Material baseline (`light/darkColorScheme()`). Blue/Green/Orange/Pink keep their hand-tuned primary / primaryContainer and get every other accent role from the seed via `AccentRoles.fromSeed` (Material tones on the seed's hue: on*, containers, secondary*, tertiary*, inversePrimary, surfaceTint, plus accent-tinted background/surface/surfaceVariant/outline/surfaceContainer*). Pure black still overrides background/surface/surfaceContainer(Low/Lowest). | `ui/theme/Theme.kt`, `ui/theme/ToneMath.kt` (new), `test/.../ui/theme/ToneMathTest.kt` (new) |
| Splash (polish, not filed) | API 31+: `LocalMusicApp` mirrors the persisted theme mode into `UiModeManager.setApplicationNightMode` (System → AUTO, Light → NO, Dark → YES). The system persists that per app and uses it for the launch splash and the `values-night` theme, whose `windowBackground` is now black. `MainActivity` declares `configChanges="uiMode"`, so a theme switch re-themes Compose live without recreating the activity. API 26–30: unchanged (the splash follows the system). | `LocalMusicApp.kt`, `AndroidManifest.xml`, `res/values-night/themes.xml` |

### How QA should re-verify
- **BUG-1**: the bugs.md repro (Aurora Lane → `Shuffle all` → in Queue tap the last row so it is current → Fault Lines rows `More options` →
  "Add to queue" ×3). Expected: Queue shows Quake, Tremor, Rift **below** the current row, in the order they were added. When the current track ends
  they play, and then state 4 (ENDED). Also: add while the current row is in the middle → the added rows land at the very end. Shuffle off → still appended
  (`149-…` regression). Logcat still shows `PlaybackService: shuffle order starts at …` when shuffle turns on.
- **BUG-2**: `uiautomator dump` on Library → `Playlists` and on Settings → Music folders: the FAB node has `content-desc="New playlist"` /
  `"Add folder"`, `clickable=true`, and resource-id `new_playlist_fab` / `add_folder_fab`. Tapping it by desc opens the dialog / SAF picker.
- **BUG-3**: Theme → `Dark` + `Pure black` + `Accent Green`: the checked `Pure black` switch thumb is dark green (not purple 56,30,114) and the track is
  light green. Also check: a slider in Equalizer (active track = accent, inactive track = muted accent), the `New playlist` FAB (accent container), the
  selected Library tab (accent text + indicator), and the same screens in `Light` and with Blue / Orange / Pink. `Accent Purple` should look as before.
- **Splash**: needs API 31+ (the AVD is 35). With the system light (`cmd uimode night no`) set the theme to `Dark` (+ `Pure black`), open the app once so the
  mode is synced, then `am force-stop dev.localmusic` and cold start: the splash is dark (black background), not white. `Follow system` → the splash
  follows the system again. Switching theme while the app is open must not restart the screen (stay on the Theme screen, no flicker loop).

### Known gaps (updated)
- The BUG-1 known gap above ("Add to queue inserts at a random play-order position") is fixed. Added items go to the end of the play order (queue
  semantics), not directly after the current track. Adding while shuffle is off and then turning shuffle on reshuffles everything after the current item, as before.
- The very first cold start after this update (before the app has synced the night mode once) can still show the old light splash.

## Play config persistence + open Now Playing on track tap

- PlaybackService saves the queue (track ids), current item, position, shuffle (with its play order) and repeat to
  `PlaybackStateStore` (SharedPreferences `playback_state`): queue on timeline/shuffle changes, position on player
  events, every 10 s while playing, and on task removal / destroy. On start it restores them paused (prepared, not
  playing); tracks that are gone or missing are dropped (`domain/PlaybackRestore`, tested in `PlaybackRestoreTest`).
  The restore is skipped if something started playing first.
- `UiStateStore` (SharedPreferences `ui_state`) keeps the Library tab and the last opened Folder/Artist/Album/Playlist
  screen; AppNavHost reopens that screen on a cold launch (Back returns to Library).
- Tapping a track row (folder/artist/album lists and playlists) starts playback and opens Now Playing.
  "Play all" / "Shuffle all" still stay on the list.
- Not yet verified on an emulator.

## Color themes (2026-10-30)

- The 5 accents became 14 **color themes** (`settings/ColorTheme`, replaces `Accent`): Purple, Blue, Green, Orange,
  Pink (the Material themes, unchanged) plus Monochrome, Catppuccin, Dracula, Everforest, Gruvbox, Nord, Rosé Pine,
  Solarized and Tokyo Night. Each has a light and a dark scheme; Mode, Pure black and Dynamic color work as before.
- Stored under the old DataStore key `accent` with the same names for the first five, so an install that had
  `Accent Green` comes up with `Theme Green`. `AppSettings.accent` → `colorTheme`, `setAccent` →
  `setColorTheme(theme)`, which also turns Dynamic color off in the same edit, on every API level (so a pick made
  below API 31 survives a restore onto Android 12+).
- `ui/theme`: `ThemePalettes` (palette data, pure), `ColorRoles` (was `AccentRoles` in ToneMath.kt; `fromSeed`
  unchanged, new `fromPalette`), `ToneMath.mix` / `ensureContrast` / `lightness`. `Theme.kt`: `colorThemeScheme`,
  `appColorScheme`, `withPureBlack` (replace `accentScheme` / `accentSwatch`). Tests: `ColorThemesTest` (new),
  `SettingsMapperTest` and `ToneMathTest` updated.
- Palette colors are kept where legible; otherwise `ensureContrast` moves only their lightness (hue kept, never more
  colorful than the palette color: Rosé Pine Dawn's rose darkens to #A6504D, not brick red). Floors (spec §7): text
  ≥ 7:1; accents and error ≥ 4.5:1 on `surfaceContainerHigh`, where dialog buttons draw them; subtext ≥ 4.5:1 and
  outline ≥ 3:1 on `surfaceContainerHighest` (the off switch's thumb sits on it).
- Theme screen: the accent swatches are replaced by a grid of preview cards (a miniature Library screen per theme, in
  the current mode and pure black). Picking a card turns Dynamic color off, as accents did. Cards are at least
  96dp × font scale wide (3 columns on a phone at the default size, 2 with large fonts); names wrap to 2 lines
  instead of being cut off.
  The grid sets its rows and columns for accessibility services explicitly (`selectableGroup()` would number the
  cards within each row).
- Elsewhere: Now Playing marks Shuffle / Repeat "on" with a dot under the primary icon (Monochrome's primary is close
  to its text color, so the color alone didn't show the state); the current queue row is `surfaceContainerHigh`
  instead of half-transparent `secondaryContainer`, so its primary title meets 4.5:1 in every theme.

### QA labels (replace the `Accent …` row)
| Element | contentDescription |
|---|---|
| Color theme cards (role RadioButton; uiautomator shows the pick as `checked=true`) | `Theme Purple`, `Theme Blue`, `Theme Green`, `Theme Orange`, `Theme Pink`, `Theme Monochrome`, `Theme Catppuccin`, `Theme Dracula`, `Theme Everforest`, `Theme Gruvbox`, `Theme Nord`, `Theme Rosé Pine`, `Theme Solarized`, `Theme Tokyo Night` |

`Theme Rosé Pine` is the only non-ASCII label: read uiautomator dumps as UTF-8, or match `Theme Ros`. In the round-3
checklist (step 9) and the BUG-3 re-verify steps, read `Theme Green` for `Accent Green`.

### How QA should verify
- Install over the existing app (keep data), with Dynamic color off: the previously picked accent shows as the
  selected card.
- Settings → Theme → each card: the whole app recolors live (Library tabs/FAB, Now Playing, Equalizer sliders), in
  `Light` and `Dark`, and with `Pure black` (black background, theme accents kept). Cards preview the current mode.
- With `Dynamic color` on (API 31+), no card is selected; tapping one turns `Dynamic color` off and applies it.
- `Theme Monochrome` → Now Playing: `Shuffle` on and `Repeat all` each show a dot under the icon; off shows none.
- Font size 200 % (Settings → Display): the Theme grid drops to 2 columns and no theme name ends in "…".
