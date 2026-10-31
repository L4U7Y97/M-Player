# Confirmed statement of intent (2026-10-26)

- **Outcome:** a fully offline Android music player for music already sitting in folders the user picks.
- **User:** the owner + a few friends; sideloaded APK (no Play Store); Android 8.0+ (minSdk 26).
- **Library:** pick one or more folders, each with an "include subfolders" toggle; rescan on demand. Browse by Folder, Artist, Album (from file tags, falling back to file/folder name).
- **Playback:** play/pause/next/prev/seek, shuffle, repeat off/all/one; sort how a folder/album/artist plays (track #, title, file name, date added; asc/desc); editable "up next" queue (reorder, remove). Background + locked-screen playback with lock-screen / notification / Bluetooth-headset controls.
- **Playlists:** create, rename, reorder, delete; stored locally; missing files flagged as missing.
- **Equalizer:** on/off, device presets, custom band sliders, bass boost; persisted. Uses android.media.audiofx (no extra libs).
- **Gapless:** album tracks play back-to-back with no gap; optional "skip silence" toggle. No crossfade.
- **Themes:** light / dark / follow system + pure-black option; several color themes, each in light and dark (the Material accent colors plus named palettes such as Nord, Dracula, Gruvbox; added 2026-10-30); Material You dynamic color on Android 12+.
- **Widget:** resizable home-screen widget: current song + prev / play-pause / next; tap opens app.
- **Success:** emulator QA covers picking a folder, browsing, playing, shuffle/repeat/sort/queue, building a playlist, toggling EQ, switching themes, the widget, and lock-screen playback, all without crashes. Unit tests cover scanning, sorting, queue, playlist and settings logic.
- **Constraints:** NO `android.permission.INTERNET` in the merged manifest. Kotlin + Jetpack Compose + Media3/ExoPlayer.
- **Out of scope:** streaming/cloud, album-art downloads, lyrics, tag editing, crossfade, Play Store release work.
