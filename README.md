# M-Player

An offline Android music player for music that's already on your phone. Point it at one or more folders and it
plays what's in them. It has no account, no streaming, and no internet access. The app doesn't request the
`INTERNET` permission at all.

## Features

- **Library**: pick one or more folders (subfolders optional) and browse by folder, artist or album. Names come
  from file tags and fall back to the file and folder names. Rescan whenever you add music. Subfolders that contain
  a `.nomedia` file are skipped.
- **Formats**: mp3, m4a, aac, flac, ogg/oga, opus, wav.
- **Playback**: shuffle; repeat off/all/one; sort by track number, title, file name or date added; an editable
  "up next" queue. Gapless playback between album tracks, with an optional "skip silence" toggle.
- **Background play**: playback continues with the screen off. You can control it from the notification, the lock
  screen and Bluetooth headsets.
- **Playlists**: create, rename, reorder and delete them. Playlists are stored on the device, and files that go
  missing are flagged.
- **Equalizer**: device presets, custom bands and bass boost.
- **Themes**: light, dark or follow system, plus a pure-black option, a few accent colors, and Material You colors
  on Android 12+.
- **Home-screen widget**: shows the current song with previous / play-pause / next buttons.

## Installing

M-Player isn't on the Play Store. You install the APK by hand (sideloading).

1. Get `app-debug.apk` from a developer.
2. Open it on your phone. Android will ask you to allow installs from the app you opened it with (Files, Chrome,
   your chat app, …). Allow it, then tap **Install**.
3. Open **M-Player**, tap **Add folder**, pick the folder your music is in, and tap "Use this folder". To add
   more folders, choose which ones include subfolders, or rescan, go to Settings → **Music folders**.

Requires Android 8.0 (API 26) or newer.

**Updating:** install the new APK over the old one. Your library, playlists and settings are kept. Every build is
signed with the same key (`app/debug.keystore`), whatever computer it was built on. If Android says the app
"conflicts with an existing package", your installed copy was signed with a different key (it was built before the
shared key was added, or on a setup that doesn't use it). Uninstall it once, which loses your playlists and settings,
then install the new APK. Future updates install over it normally.

**Permissions:**

| Permission | Why |
|---|---|
| Notifications | Shows the playback controls in the notification shade (Android 13+ asks for this) |
| Foreground service / media playback, wake lock | Keeps music playing with the screen off |
| Modify audio settings | Lets the equalizer and bass boost work on some phones |

The app can read only the folders you pick. It never sees the rest of your storage.

## Building from source

You'll need JDK 21 and the Android SDK (platform 35, build-tools 35.0.0). Point Gradle at the SDK with
`sdk.dir=…` in `local.properties`, or with `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # JVM unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `.\gradlew.bat`. If `clean` fails with "Unable to delete … classes.jar", run `.\gradlew.bat --stop`
first.

Debug builds are signed with `app/debug.keystore`, not your machine's `~/.android/debug.keystore`. That way APKs
from different computers can update each other without an uninstall. Keep the file in git and don't regenerate it.
If you change the key, everyone with the app installed has to uninstall it and loses their data.

The app is built with Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room and DataStore. Code lives under
`app/src/main/java/dev/localmusic/`:

| Package | What's in it |
|---|---|
| `data/scan` | Folder scanning (Storage Access Framework), tag reading, filename fallbacks |
| `data/db`, `data/repo` | Room database, library and playlist repositories |
| `domain` | Pure Kotlin logic: sorting, queue, shuffle order, repeat, playlist ordering |
| `playback` | `PlaybackService` (Media3 session), player connection, equalizer, saved playback state |
| `settings` | Persisted settings (theme, EQ, sort, UI state) |
| `ui` | Compose screens: library, track lists, now playing, queue, playlists, settings |
| `widget` | Home-screen widget |

Most of the logic sits in `domain` and `data/scan` and is unit-tested in `app/src/test/` with no device needed.
The manifest strips `INTERNET` and `ACCESS_NETWORK_STATE` in case a library tries to merge them in, so keep it
that way. The app is meant to stay offline.

## Project docs

[`docs/spec.md`](docs/spec.md) covers the architecture and the main design decisions, and
[`docs/intent.md`](docs/intent.md) covers what's in and out of scope. The app was built by a team of AI agents
(planner, developer, tester and QA), who coordinated through [`docs/agents/`](docs/agents/README.md).
