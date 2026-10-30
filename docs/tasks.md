# Local Music — tasks

Developer: work top to bottom, tick `[x]` when the acceptance criteria hold, keep `.\gradlew.bat assembleDebug`
green at the end of every phase. Spec references are to `docs/spec.md`. `[TEST]` = pure class the tester covers in
`app/src/test/` (JUnit 4, no Robolectric); keep those classes free of `android.*` imports.

## Phase 1 — Scaffold + build green
- [x] T1 Create Gradle project: wrapper 8.14.3, `settings.gradle.kts`, root + `app/build.gradle.kts`, `libs.versions.toml` with exactly the versions in spec §1, `gradle.properties`, `local.properties` (sdk.dir).
  - AC: `.\gradlew.bat assembleDebug` succeeds (with `JAVA_HOME` = the JDK 21 path); no `jvmToolchain`.
- [ ] T2 `LocalMusicApp`, `AppContainer` (empty stubs), `MainActivity` with Compose "Local Music" placeholder; manifest with app label, permissions and INTERNET/ACCESS_NETWORK_STATE `tools:node="remove"` (spec §9).
  - AC: app installs and launches on emulator; merged manifest and `aapt dump permissions` show no INTERNET.
  - Dev note (round 1): code + manifest done; build green; merged manifest + aapt show no INTERNET. Emulator install/launch not yet verified (QA).
- [x] T3 `app/src/test` wired (junit 4.13.2, coroutines-test) with one trivial test.
  - AC: `.\gradlew.bat testDebugUnitTest` passes.
  - Dev note (round 1): junit 4.13.2 + coroutines-test wired; the trivial test is left to the tester (tester owns app/src/test).
  - Dev note (round 3): the tester's suite exists (146 tests incl. round-3 additions) and `testDebugUnitTest` passes.

## Phase 2 — Data, scanning, database
- [x] T4 Room entities/DAOs/`LocalMusicDatabase` per spec §3 (folders, tracks, playlists, playlist_entries) incl. grouping queries (Folders by parentPath, Artists, Albums) as Flows filtering `missing = 0`.
  - AC: compiles via KSP; DAO methods exist for every grouping and per-key track list.
- [x] T5 `ScanRules` [TEST]: supported extensions, hidden-file skip, `shouldDescend(depth, includeSubfolders)`, `childPath`.
  - AC: `song.MP3` accepted, `.hidden.mp3`/`cover.jpg`/`notes.txt` rejected; depth ≥ 1 descends only when includeSubfolders.
- [x] T6 `TagFallbacks` [TEST] per spec §4.
  - AC: blank title → file name sans extension; no artist & no albumArtist → "Unknown artist"; no album → parent folder name; `"3/12"` → 3, `"abc"` → null.
- [ ] T7 `FolderScanner` (DocumentsContract BFS) + `TagReader` (MediaMetadataRetriever, released in finally) + incremental reuse by lastModified/size.
  - AC: scanning a folder with nested subfolder returns nested tracks only when includeSubfolders = true; a corrupt file doesn't abort the scan.
  - Dev note (round 1): implemented (FolderScanner, TagReader); not yet verified on a device (no folder-picker UI until T20).
  - Dev note (round 3): subfolders containing `.nomedia` are now skipped with everything below them (`ScanRules.shouldSkipDirectory`); the picked root is never skipped. Unit-tested; device check by QA.
- [ ] T8 `LibraryRepository`: addFolder (persist permission), removeFolder, setIncludeSubfolders (triggers rescan), rescanAll with missing marking, `scanState` flow.
  - AC: deleting a file and rescanning marks it missing (hidden from library, kept for playlists); removing a folder releases its permission.
  - Dev note (round 1): implemented (LibraryRepository); not yet verified on a device.

## Phase 3 — Playback service
- [ ] T9 `PlaybackService` (MediaSessionService, ExoPlayer config, session activity, onTaskRemoved) + manifest service entry (spec §5).
  - AC: playing a track shows a media notification with prev/play/next; playback continues with screen off; lock screen shows controls.
  - Dev note (round 2): implemented; on the `test` AVD (API 35) Play all started a foreground media notification (prev/play actions, title) and playback kept going with the app in the background. Screen-off + lock-screen controls not checked yet (QA).
- [x] T10 `MediaItemMapper` + session callback restoring URIs in `onAddMediaItems`.
  - AC: items set from the controller play (no "no URI" error in logcat).
  - Dev note (round 2): verified: 3 WAV items set from the controller played, no URI error.
- [x] T11 `PlayerConnection` (MediaController, `PlayerUiState` flow, all commands in spec §5) following the **gapless rule**.
  - AC: a 3-track album plays through back-to-back without re-prepare (logcat shows a single `prepare`); `playTracks(..., shuffle=true)` enables shuffle mode.
  - Dev note (round 2): verified: logcat shows one `PlayerConnection: prepare() with 3 items` and `PlaybackService: transition -> … (reason 1)` (AUTO) for tracks 2 and 3; shuffle=true enables shuffle mode (Queue showed shuffled play order).
- [x] T12 `RepeatCycler` [TEST] and `QueueModel` [TEST] (move/remove current-index adjustment, play order).
  - AC: OFF→ALL→ONE→OFF; removing an item before current decrements current index; moving the current item updates current index to its new position.
  - Dev note (round 1): pure RepeatCycler + QueueModel written early for the tester (API in handoff-dev.md).
  - Dev note (round 2): pure classes covered by the tester (handoff-test.md, all passing).
- [x] T13 Headset/Bluetooth + audio focus + becoming-noisy behavior (configured in T9) verified.
  - AC: `adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_MEDIA_NEXT` control playback while app is backgrounded.

  - Dev note (round 2): verified: with the app backgrounded (HOME), `KEYCODE_MEDIA_PLAY_PAUSE` started/paused and `KEYCODE_MEDIA_NEXT` was handled by the session. Audio focus/noisy configured in the service.
## Phase 4 — UI screens
- [x] T14 Theme scaffold (`LocalMusicTheme` reading defaults), `AppNavHost` with routes of spec §10, root `testTagsAsResourceId`.
  - AC: navigation between all routes works with system back and "Back" icon.
  - Dev note (round 2): all routes of spec §10 exist (equalizer/theme are placeholders until Phase 6); Back icon and system back verified on folder detail, Now Playing, Queue.
- [x] T15 Library screen: tabs Folders/Artists/Albums (Playlists tab placeholder until T21), empty state with "Add folder".
  - AC: after adding a folder, all three tabs list groupings with track counts.
  - Dev note (round 2): verified: after adding TestMusic via SAF, Folders tab listed "TestMusic · 3 tracks" and "Nested · 1 track". Artists/Albums tabs use the same DAO groupings (not individually screenshotted).
- [ ] T16 `SortSpec` + `TrackSorter` [TEST] (spec §6 exactly) and `TrackListScreen` with Sort menu, Play all, Shuffle all, row tap, "More options" → Add to queue.
  - AC: changing sort reorders the visible list and is persisted; Play all starts at track 1 of the sorted order; null track numbers sort last both ways.
  - Dev note (round 1): pure SortSpec + TrackSorter written early; TrackListScreen pending.
  - Dev note (round 2): TrackListScreen with Sort menu, Play all, Shuffle all, row tap, More options → Add to queue / Add to playlist done; Play all from track 1 verified. Sort re-ordering + persistence across restart not yet checked on device (QA).
  - Dev note (round 3): TITLE / FILE_NAME (and the fileName tie-break) now use natural case-insensitive order (`NaturalOrder`: "Track 2" before "Track 10"). Unit-tested.
- [x] T17 MiniPlayer on library/detail screens ("Open now playing", Play/Pause, Next).
  - AC: mini player appears once something is loaded and reflects play state within 1 s.
  - Dev note (round 2): verified: mini player appeared after Play all with "Open now playing", "Pause", "Next".
- [ ] T18 Now Playing screen (seek bar, times, Previous/Play-Pause/Next, Shuffle toggle, Repeat 3-state, Queue, Add to playlist).
  - AC: every control works and its content description matches spec §11 for its current state; seeking updates position.
  - Dev note (round 2): all controls implemented with spec §11 descriptions (Shuffle desc is on the checkable node, Repeat cycles off/all/one, verified). Seeking not yet checked on device (QA).
- [ ] T19 Queue screen in play order; jump, Move up/Move down (disabled while shuffle on), Remove from queue.
  - AC: reordering changes what plays next without interrupting the current track; removing the current item advances to the next.
  - Dev note (round 2): play order, jump, Move up/down (disabled while shuffle on) and Remove implemented; play-order display verified with shuffle on/off. Reorder-without-interrupt and remove-current not yet checked on device (QA).
  - Dev note (round 3): Queue no longer highlights any row once the player is ENDED (e.g. the current last item was removed and ExoPlayer parked on item 0). "Shuffle all" now plays every track once: the service installs a shuffle order starting at the current item whenever shuffle turns on.
- [ ] T20 Settings → Music folders screen (Add folder, Include subfolders switch, Remove folder, Rescan, progress text, "Access lost").
  - AC: toggling Include subfolders off and rescanning removes nested-folder tracks from the library.

  - Dev note (round 2): screen implemented (Add folder, Include subfolders switch row, Remove folder, Rescan, progress text, "Access lost"). Toggle-off-and-rescan AC not yet checked on device (QA).
## Phase 5 — Playlists
- [x] T21 `PlaylistOrdering` [TEST] (move/remove/append, renumbering, out-of-range no-op).
  - AC: positions are always 0..n-1 after every op; duplicates allowed.
  - Dev note (round 1): pure PlaylistOrdering written early.
  - Dev note (round 2): pure class covered by the tester (all passing).
- [ ] T22 `PlaylistRepository` + Playlists tab (New playlist, Rename playlist, Delete playlist with confirm) and "Add to playlist" dialog from track rows and Now Playing.
  - AC: create → add 3 tracks → rename → delete all persist across app restart.
  - Dev note (round 2): PlaylistRepository + Playlists tab (New/Rename/Delete with confirm) + Add to playlist dialog (track rows, Now Playing) implemented; persistence across restart not yet checked on device (QA).
- [ ] T23 Playlist detail: Play all / Shuffle all, Move up / Move down / Remove from playlist, missing tracks greyed "Missing" and skipped on play.
  - AC: reorder persists after restart; a playlist containing a deleted file shows it as Missing and plays the others.

  - Dev note (round 2): playlist detail implemented (Play all / Shuffle all, Move up/down, Remove from playlist, missing rows greyed "Missing" and skipped). Not yet checked on device (QA).
## Phase 6 — EQ, themes, skip silence
- [x] T24 `SettingsRepository` (DataStore) + `AppSettings`/`EqSettings` + `SettingsMapper` [TEST] (enum fallback, CSV band levels with clamping, strength clamp).
  - AC: unknown stored theme string → SYSTEM; `"-300,x,99999"` with range ±1500 → [-300, 0, 1500].
  - Dev note (round 1): pure AppSettings/EqSettings/SettingsMapper written early; SettingsRepository (DataStore) pending.
  - Dev note (round 3): DataStore SettingsRepository in use since round 2; SettingsMapper ACs covered by the tester (passing).
- [ ] T25 `AudioEffectsController` with app-generated audio session id, applied to the player; Equalizer screen (enabled, Preset, band sliders, Bass boost + strength); graceful "not available" state.
  - AC: settings survive app restart and are re-applied when playback starts; choosing a preset moves the band sliders; no crash if effects are unsupported.
  - Dev note (round 3): coded: `playback/AudioEffectsController` (Equalizer + BassBoost on `container.audioSessionId`, started by the service and the EQ screen, re-applies persisted settings, preset → read back + save band levels), `ui/settings/EqualizerScreen`, "Equalizer not available on this device" fallback. Restart/re-apply/preset-moves-sliders to be verified on device (QA).
- [ ] T26 Theme screen: Follow system / Light / Dark, Pure black, Dynamic color (API 31+), 5 accents; applied live.
  - AC: switching Dark + Pure black gives a black background immediately; choice persists after restart.
  - Dev note (round 3): coded: `ui/settings/ThemeScreen` (Follow system/Light/Dark radios, Pure black, Dynamic color on 31+, 5 accents; an accent pick turns Dynamic color off). Applied live through `LocalMusicTheme`; system-bar icons follow the app theme. Device check by QA.
- [ ] T27 "Skip silence" switch wired to `player.skipSilenceEnabled`.
  - AC: toggle persists and the service applies it while playing (log line or debug check).

  - Dev note (round 2): hook done early: Settings switch "Skip silence" persists; service sets skipSilenceEnabled and logs it. Left for Phase 6 verification.
  - Dev note (round 3): unchanged code from round 2 (service sets `skipSilenceEnabled` and logs `PlaybackService: skipSilenceEnabled = <bool>`). QA: toggle while playing and check logcat.

## Phase 7 — Widget
- [ ] T28 RemoteViews widget (spec §8): layout, provider + `appwidget-provider` XML (resizable), `WidgetStateStore`, `WidgetRenderer`, service listener updates, media-button PendingIntents, inactive → open app.
  - AC: widget shows current title/artist and updates on track change and play/pause within ~1 s.
  - AC: Widget previous / play / next control playback from the home screen; tapping the text opens the app; no crash when tapped with nothing playing.
  - Dev note (round 3): coded: `widget/NowPlayingWidgetProvider` + `WidgetRenderer` + `WidgetStateStore`, layout `widget_now_playing`, info `xml/now_playing_widget_info`. Buttons send broadcasts to the provider, which sends the command through a short-lived MediaController (deviation from §8's getForegroundService, see handoff-dev Round 3). Device check by QA.

## Phase 8 — Polish + final build
- [ ] T29 POST_NOTIFICATIONS request on API 33+ at first launch; denial doesn't break playback.
  - AC: fresh install on API 34 emulator shows the prompt once.
  - Dev note (round 2): coded: asked once on API 33+ (SharedPreferences flag). Prompt not checked on a fresh install (QA).
  - Dev note (round 3): no change; still needs the fresh-install prompt check (QA).
- [ ] T30 (optional) Queue persistence + `onPlaybackResumption` + Media3 `MediaButtonReceiver` (spec §5).
  - AC: after force-stop, headset play resumes the last track at the saved position.
  - Dev note (round 3): not done (optional). No MediaButtonReceiver in the manifest, on purpose.
- [ ] T31 Robustness pass: no main-thread scanning/DB, scan on IO, empty states on every list, rotation keeps state.
  - AC: StrictMode-free launch; rotating Now Playing keeps playback and UI state.
  - Dev note (round 3): reviewed: scans/DB on IO or app scope, empty states on all lists (incl. EQ unavailable), Compose/NavHost state survives rotation. StrictMode launch + rotation check left to QA.
- [x] T32 Final: `.\gradlew.bat clean assembleDebug testDebugUnitTest` green; INTERNET check (spec §9) clean; write `handoff-dev.md` with build/install steps, APK path, the spec §11 label table plus any additions, known gaps.
  - AC: all three commands pass and handoff file exists.
  - Dev note (round 3): `clean :app:assembleDebug :app:testDebugUnitTest` green (146 tests); merged manifest + aapt show no INTERNET; handoff-dev.md Round 3 has build steps, APK path, label table additions, known gaps, QA checklist.

## Tester coverage summary
`ScanRules` (T5), `TagFallbacks` (T6), `RepeatCycler` + `QueueModel` (T12), `SortSpec`/`TrackSorter` (T16),
`PlaylistOrdering` (T21), `SettingsMapper` (T24). Report results in `handoff-test.md`, bugs in `bugs.md`.
