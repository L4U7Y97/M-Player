# QA handoff

## Round 1 (2026-10-26) — emulator run against handoff-dev Round 3

### Setup
- Device: AVD `test`, serial `emulator-5554`, Android 15 (API 35), 1080x1920. Started with
  `emulator -avd test -memory 2048 -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect` (windowed). **Left running.**
- Package: `dev.localmusic`, activity `.MainActivity`. APK `app/build/outputs/apk/debug/app-debug.apk` (built 13:15; no source file is newer).
- Install: `adb uninstall dev.localmusic` (clean state), then `adb install -r app\build\outputs\apk\debug\app-debug.apk`, then
  `adb shell pm grant dev.localmusic android.permission.POST_NOTIFICATIONS`. The notification prompt itself was not exercised.
- Test data: a generator script (scratchpad `make_mp3s.py`, no encoder or downloads) made 13 tagged MP3s: MPEG-1 L3, 32 kbps mono, a quiet
  steady tone, ID3v2.3 TIT2/TPE1/TALB/TRCK, 12–14 s each. They were pushed to `/sdcard/Music/QA/`:
  - root: Aurora Lane / *Morning Tides* (Sunrise, Harbor, Gulls)
  - `Sub/NightShift/`: Aurora Lane / *Night Shift* (Lanterns, Neon, Afterhours)
  - `Numbers/`: Basalt Echo / *Numbers* (`Track 10`, `Track 2`, `track 1`, with track numbers 1, 2, 3; `Track 3` was added later for the date-added sort)
  - `FaultLines/`: Basalt Echo / *Fault Lines* (Quake, Tremor, Rift)
  - `Hidden/` with `.nomedia` + "Secret Song" (artist "Should Not Appear")
- Method: uiautomator XML dumps, then summaries, then the center of each node's bounds is tapped. Every coordinate came from XML. Evidence is in
  `LocalMusic/android-qa-output/` (NN-name.xml/.txt/.png are steps in order). Logs: `logcat-app.txt`, `logcat-full.txt`, `crash.txt`.

### Results (PASS 27 / FAIL 1 / BLOCKED 1)

| # | Check | Result | Evidence / notes |
|---|---|---|---|
| 1 | Install + launch, empty state "No music folders yet" | PASS | `01-launch-empty.png` |
| 2 | Add folder via SAF (DocumentsUI → Music → QA → USE THIS FOLDER → ALLOW) | PASS | `02-saf.txt`, `04-saf-qa.png`, `05-allow.txt`, `06-folders-tab.png` (4 folders, 12 tracks) |
| 3 | `.nomedia` subfolder skipped (initial scan, and a `.nomedia` added to FaultLines → Rescan → 10 files, removed → 13) | PASS | log `FolderScanner: Skipping QA/Hidden: contains .nomedia`; `96-rescan-nomedia.txt`, `97-rescan-restored.txt`. "Secret Song"/"Should Not Appear" never listed |
| 4 | Include subfolders off → only root (3 tracks, 1 album); on → 13 files | PASS | `77-subfolders-off.png`, `78-folders-tab-suboff.txt`, `78b-albums-suboff.txt`, `95-subfolders-on.png` |
| 5 | Rescan picks up an added file and drops a removed one | PASS | 12→13 after pushing `Track 3.mp3`; 13→12 after moving Gulls away (`144.txt`) |
| 6 | Browse Folders / Artists / Albums (tags read; rows open track lists) | PASS | `06-folders-tab.png`, `07-artists.png` (2 artists × 6), `08-albums.png` (4 albums × 3), `09-album-numbers.png`, `33-artist-aurora.txt` |
| 7 | Play all → mini player `Pause`, one `prepare()` | PASS | `19-playing.png`, `play-numbers-gapless.log` |
| 8 | Pause / Play / Previous (restart, then previous item) / Next | PASS | `26-r1-play.txt` … `29-next.png` (paused time frozen at 0:08 across 3 s) |
| 9 | Seek (tap + drag in `Seek bar`) | PASS | `22-seek-25.txt`, `23-seek-drag.png`; buffering → ready after each seek |
| 10 | Gapless: album plays with one `prepare()` and AUTO transitions (reason 1) | PASS (log level) | `play-numbers-gapless.log`. Audible gaps can't be measured on the emulator |
| 11 | Shuffle all: `Shuffle` checked=true, current row first, every track played exactly once, then ENDED | PASS | `35-np-shuffle.xml`, `36-queue-shuffle.png`, `shuffle-playthrough.log` (6 distinct transitions, then state 4); top bar "Shuffle is on: reordering disabled" |
| 12 | Repeat off → all → one → off cycle; repeat one loops (reason 0); repeat all wraps last → first | PASS | `25-repeat-1..4.txt`, logcat in run (track 1 → track 1 reason 0; track 1 → Track 10 reason 1) |
| 13 | Sort: Track number / Title / File name / Date added × asc/desc, natural order | PASS | Title asc `track 1, Track 2, Track 10`; desc reversed; File name same; Track# asc `Track 10(1), Track 2(2), track 1(3)`; Date added desc puts the newer `Track 3` first, ties broken by file name asc (`11-…`–`18-…`, `100-…`–`102-…`) |
| 14 | Sort persisted across force-stop + relaunch | PASS | `133-relaunch-sort.txt` (Title desc kept) |
| 15 | Queue: Move up / Move down without interruption; remove other row; remove current → next track; remove current when last → no row highlighted, `Play` | PASS | `45-moveup.txt`, `46-movedown.txt`, `47-remove.png`, `49-remove-current.txt`, `52-remove-last.png`, `53-np-after.txt` |
| 16 | Add to queue, shuffle **off**: appended after current | PASS | `149-queue-add-linear.txt` |
| 17 | Add to queue, shuffle **on**: added tracks must play | **FAIL — BUG-1** | `41-queue-added.png`, `bug-shuffle-addtoqueue.log` |
| 18 | Playlist: create, add 3 tracks from rows, add from Now Playing, Move up/down, Remove, Rename, create+Delete (confirm dialog) | PASS (+BUG-2 minor) | `57-pl-created.png`, `62-pl-detail.txt`, `63-…`–`65-pl-remove.png`, `67-pl-after-np-add.txt`, `70-renamed.txt`, `73-delete-confirm.txt`, `74-deleted.png`. The `New playlist` FAB has no accessible label (BUG-2) |
| 19 | Playlist missing file: shows "Missing", not tappable, skipped by Play all | PASS | `146-playlist-missing.png`; log `prepare() with 1 items` |
| 20 | Persistence after force-stop + relaunch: folder, playlist (renamed, 3 tracks), sort, EQ, theme, skip silence | PASS | `131-relaunch.png`, `132-relaunch-playlists.txt`, `134-relaunch-settings.txt`, `135-relaunch-eq.txt`, `137-relaunch-theme.txt` |
| 21 | Equalizer: enable, Preset → Rock moves sliders (+5/+3/−1/+3/+5 dB), dragging 60 Hz → Custom (+9.3 dB), Bass boost on + strength 60% | PASS | `104-eq.txt`, `107-eq-rock.png`, `108-eq-drag.txt`, `109-eq-bass.png`; `AudioEffects: applied EqSettings(…)` for each change and on relaunch |
| 22 | Theme: Dark, Pure black (bg 0,0,0), Accent Green (Dynamic color turns off), Light, Follow system (light and dark via `cmd uimode night`), Dynamic color back on; live, no restart | PASS (+BUG-3 cosmetic) | `111-theme-default.png`, `112-theme-dark.png`, `113-theme-pureblack.png`, `114-theme-green.png`, `115-theme-light.png`, `116-theme-system.png`, `118-theme-system-dark.png`, `117-theme-dynamic-on.png` |
| 23 | Skip silence toggle → `PlaybackService: skipSilenceEnabled = true`; persisted | PASS | `121-skipsilence-on.png`; relaunch log line |
| 24 | Widget: added via long-press → Widgets → search "Local" → Add; shows title/artist; Widget play/pause/next/previous work; tap text opens app; empty state "Not playing" and a button opens the app | PASS | `86-widget-home.png`, `88-widget-playing.txt`, `91-widget-paused.png`, `129-widget-next2.png`, `130-widget-prev2.txt`, `154-widget-empty.png`, `dumpsys-appwidget.txt`. No `ForegroundServiceStartNotAllowedException` |
| 25 | Screen off (`keyevent 26`) while playing: playback continues | PASS | `lock-media-session.txt` (PLAYING, positions advance, tracks change with screen off; `mWakefulness=Dozing`) |
| 26 | Media notification + system media controls; headset/media keys | PASS | `dumpsys-notification.txt` (id 1001, MediaStyle, FOREGROUND_SERVICE, vis=PUBLIC, actions prev/pause/next), `126-notification-shade.png`, `media-keys.txt` (MEDIA_PAUSE/NEXT/PLAY/PREVIOUS) |
| 27 | Lock-screen media controls UI | **BLOCKED** | The AVD has no keyguard (`locksettings get-disabled` → true), so wake goes straight to the app. Enabling a lock screen means changing device security settings, which QA did not do. The notification is PUBLIC MediaStyle, so it should show on a real lock screen |
| 28 | No INTERNET permission | PASS | `aapt-permissions.txt`: POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, MODIFY_AUDIO_SETTINGS, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION |
| 29 | No crashes during the whole run | PASS | `crash.txt` empty (`logcat -b crash`), no AndroidRuntime/ANR for dev.localmusic in `logcat-full.txt`; 0 `E` lines in `logcat-app.txt` |

### Bugs filed (docs/agents/bugs.md)
- BUG-1 [open] With shuffle on, "Add to queue" inserts tracks before the current item, so they never play (the gap the developer reported, now reproduced).
- BUG-2 [open] "New playlist" and "Add folder" extended FABs have no accessible label (NAF node). Low severity.
- BUG-3 [open] Accent color schemes keep the default purple "on" colors (purple switch thumb with Green accent). Cosmetic.

### Observations (not filed)
- Cold start with app theme = Dark while the system is light: the system splash is light (purple icon on white) for about 2 s, then the app is
  black (`138-coldstart-a..d.png`). The window background follows the system, not the app setting. Polish only.
- Widget Next at the end of a finished queue (repeat off) does nothing (`MCImplBase: Controller isn't allowed to call command= 9`). This is expected.
- After `am force-stop`, the launcher shows Android's disabled-widget placeholder until the app runs again. This is OS behavior.
- SystemUI logs `MediaResumeListener: Cannot resume with … PlaybackService`. Resumption (T30) is not implemented, as handoff-dev notes.
- uiautomator can't dump the expanded notification shade (it never goes idle while the seek bar animates), so the shade controls were checked from
  the screenshot, the notification actions, and media key events.

### Blockers
- Lock-screen UI controls are untestable on this AVD without enabling a device lock (a security setting). Everything else is covered.

### Device state left
Emulator `emulator-5554` running. App installed with the QA folder granted. Playlist "Road Trip" exists. Theme is Dark + Pure black + Green, EQ is on (Custom + bass boost), skip silence is on, sort is Title descending. The widget is on the home screen.
Test files are in `/sdcard/Music/QA` (13 tracks + Hidden) and `/sdcard/Music/TestMusic` (the old WAVs, not added to the app).

## Re-verify round (2026-10-26) — handoff-dev "Bug-fix round"

- Device: `emulator-5554` (AVD `test`, API 35). Install: `adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk` (APK built 15:40, not rebuilt; app data kept).
  `aapt dump permissions` is unchanged and lists no INTERNET (`rv/aapt-permissions.txt`). All evidence is in `android-qa-output/rv/`.
- Environment note: right after install, the guest was thrashing (load average 15–36, 95% kernel CPU). System-wide ANRs hit gms, vending, ext.services
  and permissioncontroller, and one also hit `dev.localmusic` ("executing service PlaybackService, waited 20308ms") (`rv/anr-summary.txt`).
  This happened while the whole system was stalled. QA rebooted the emulator (`adb reboot`) and saw no app ANR after that. Treat it as environmental
  unless it shows up on a healthy device.

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | BUG-1: shuffle on, current = last in play order, add Tremor/Rift/Quake → they appear below the current row in added order, play, then ENDED | PASS → closed | `38-queue-after-add.png`, `bug1-fixed-playthrough.log` |
| 2 | BUG-1: current mid-queue → added track goes to the very end; shuffle-on log `shuffle order starts at …` still present | PASS | `48-queue-mid-insert.png`, `shuffle-all-playthrough.log` |
| 3 | BUG-2: FABs `new_playlist_fab` / `add_folder_fab` carry desc `New playlist` / `Add folder` (desc is on the child node with the same bounds); no NAF nodes; tapping by desc opens the dialog / folder picker | PASS → closed | `15-playlists-fab.xml`, `16-newpl-dialog.txt`, `18-folders-fab.xml`, `19-saf-from-fab.txt` |
| 4 | BUG-3: Dark + Pure black switch thumb follows the accent (Green/Blue/Orange/Pink); Purple unchanged; Light correct; EQ sliders, tab indicator and FAB use the accent | PASS → closed | `21-theme-dark-green.png`, `22-dark-*.png`, `23-light-*.png`, `26-eq-green.png`, `27-library-green.png` |
| 5 | Splash: theme Dark with the system light → cold-start splash black (also the first cold start after reboot); Follow system → splash light; Light ↔ Dark switches live with no activity restart (same ActivityRecord) | PASS | `10-coldstart-*.png`, `13-coldstart2-*.png`, `89-coldstart-system-*.png`, `91-back-to-dark.png` |
| 6 | Smoke: Play all (one `prepare()`, mini player `Pause`) | PASS | `56-playall.txt` |
| 7 | Smoke: Shuffle all plays every track exactly once, then ENDED; start index and order are random across runs | PASS | `shuffle-all-playthrough.log`, `59-shuffle-queue.txt` |
| 8 | Smoke: queue Move up / Move down / Remove (shuffle off) without changing the current track | PASS | `52-moveup.txt`, `53-movedown.txt`, `54-remove.png` |
| 9 | Smoke: playlist add (Road Trip 3 → 4 tracks; restored Gulls no longer Missing) | PASS | `62-playlist-add.txt`, `63-roadtrip.png` |
| 10 | Smoke: EQ toggle off/on → `AudioEffects: applied EqSettings(enabled=false/true …)` | PASS | `66-eq-off.png`, `67-eq-on.txt` |
| 11 | Smoke: theme switch (covered by #4/#5) | PASS | as above |
| 12 | Smoke: widget pause / next / previous / play, title and icon update | PASS | `80-…`–`84-widget-play.png`, `widget-commands.log` |
| 13 | Smoke: screen off while playing → PLAYING, track changes with repeat all | PASS | `screen-off-media-session.txt` |
| 14 | Lock-screen media controls UI | BLOCKED | No keyguard on this AVD. Device security settings left unchanged |
| 15 | `logcat -b crash` empty | PASS | `rv/crash.txt` (0 bytes), `rv/logcat-full.txt` |

Totals: PASS 14, FAIL 0, BLOCKED 1. Bugs: BUG-1, BUG-2 and BUG-3 are `[closed]` in bugs.md. No new bugs.
End state: theme Dark + Pure black + Green, sort Title descending, EQ on. The emulator was shut down with `adb -s emulator-5554 emu kill`.
