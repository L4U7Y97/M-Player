# Bugs

Format: `## BUG-<n> [open|fixed|closed] <title>` (closed = QA re-verified the fix on device), then repro, expected, actual, evidence, file hints. Evidence paths are relative to
`LocalMusic/android-qa-output/`.

## BUG-1 [closed] With shuffle on, "Add to queue" inserts tracks before the current item, so they never play
- **Fix (dev, bug-fix round):** the player now uses `AppendingShuffleOrder` (playback/AppendingShuffleOrder.kt, math in pure `domain/ShuffleOrderMath`, 11 unit tests): items added while shuffled are appended to the END of the play order, existing order unchanged.
- **QA re-verify (2026-10-26, APK 15:40):** closed. Same repro: the queue showed Lanterns (current, last) followed by Tremor, Rift, Quake, in the order they were added. They then played and the queue ENDED (`rv/38-queue-after-add.png`, `rv/bug1-fixed-playthrough.log`). With the current item mid-queue, the added track went to the very end (`rv/48-queue-mid-insert.png`).
Found by QA on 2026-10-26, AVD `test` (API 35). The developer had already flagged this as a known gap in handoff-dev Round 3. QA reproduced it.
- Repro: Artists → Aurora Lane → `Shuffle all` (6 tracks), repeat off. In Queue, tap the 5th row, so that the current item is the last one in play
  order. Then Albums → Fault Lines → each row's `More options` → "Add to queue" (Quake, Tremor, Rift). Open Now Playing → `Queue`.
- Expected: the added tracks play after the current track, so they appear below the current row.
- Actual: the play order was Quake, Tremor, Neon, Afterhours, Harbor, Rift, Gulls, Sunrise, **Lanterns (current)**. All three added tracks landed
  above the current row. When Lanterns finished, playback ENDED (`playbackState = 4`) without playing any of them.
- Evidence: `41-queue-added.png`, `41-queue-added.txt`, `bug-shuffle-addtoqueue.log`.
- File hint: `playback/PlayerConnection.kt` `addToQueue()` calls `c.addMediaItems(...)`, and Media3's `DefaultShuffleOrder.cloneAndInsert` puts new
  indices at random positions in the shuffle order. Possible fix: after adding, install a shuffle order that keeps the current play order and
  places the new indices right after the current index (or at the end). The existing `ShuffleFromCurrentListener` / `QueueModel` helpers fit this.
  With shuffle off, add-to-queue appends correctly (`149-queue-add-linear.txt`: Harbor|Tremor).

## BUG-2 [closed] "New playlist" and "Add folder" extended FABs have no accessible label (uiautomator NAF node)
- **Fix (dev, bug-fix round):** both FABs get `Modifier.testTag("new_playlist_fab"/"add_folder_fab").semantics { contentDescription = "New playlist"/"Add folder" }` on the clickable node.
- **QA re-verify (2026-10-26):** closed. The XML has no NAF nodes. A clickable node with id `new_playlist_fab` / `add_folder_fab` contains a child with desc `New playlist` / `Add folder` and the same bounds. Tapping by desc opened the "New playlist" dialog and the folder picker (`rv/15-playlists-fab.xml`, `rv/16-newpl-dialog.txt`, `rv/18-folders-fab.xml`, `rv/19-saf-from-fab.txt`). Note: in the dump the desc sits on that child node, not on the clickable node itself. TalkBack and label-based taps work either way.
- Repro: Library → `Playlists` tab, then dump the UI (`uiautomator dump`). Same on Settings → Music folders (`Add folder` FAB).
- Expected: the FAB node exposes text or a content-desc of "New playlist" / "Add folder", as handoff-dev's QA label table lists. TalkBack should read it.
- Actual: the FAB is `class=android.view.View NAF="true" text="" content-desc=""` (bounds `[648,1500][1038,1647]` on Playlists and
  `[677,1668][1038,1815]` on Music folders). The visible text "New playlist" never appears in the tree. QA had to tap it from its bounds.
  The empty-library `Add folder` button (a plain button, not a FAB) is labelled correctly.
- Evidence: `55-newpl.xml`, `55-playlists-tab.png`, `76-folders.txt`.
- File hint: `ui/library/LibraryScreen.kt:149` and `ui/settings/FoldersScreen.kt:101` (`ExtendedFloatingActionButton(text = { Text(...) })`).
  Adding `Modifier.semantics { contentDescription = "New playlist" }` (and `testTag`) on the FAB would expose it.
  Severity: low (it works by touch, but it is an accessibility gap and breaks label-based automation).

## BUG-3 [closed] Accent color schemes keep the default purple "on" colors, so a green/blue/orange/pink theme shows purple switch thumbs
- **Fix (dev, bug-fix round):** `accentScheme()` now sets every accent role (on*, containers, secondary, tertiary, inversePrimary, surfaceTint, accent-tinted neutrals) from the seed via `AccentRoles.fromSeed` (ui/theme/ToneMath.kt, 4 unit tests); Purple = Material baseline.
- **QA re-verify (2026-10-26):** closed. With Dark + Pure black, the checked switch thumb now matches the accent: Green (23,55,25), Blue (16,49,86), Orange (80,37,0), Pink (86,26,57). Purple is unchanged (56,30,114). Light mode shows a white thumb on the accent track. With Green, the EQ sliders, the library tab indicator and the `New playlist` FAB container are all green (`rv/21-theme-dark-green.png`, `rv/22-dark-*.png`, `rv/23-light-*.png`, `rv/26-eq-green.png`, `rv/27-library-green.png`).
- Repro: Settings → Theme → `Dark` → `Pure black` on → `Accent Green`.
- Expected: every accent-related color follows the chosen accent.
- Actual: switch tracks turn green (149,215,142), but the checked switch **thumb** is purple (56,30,114) = Material's default dark
  `onPrimary` (0xFF381E72). Light mode shows the same mismatch for the other `on*` / container roles.
- Evidence: `114-theme-green.png` (Pure black switch), pixel samples taken from that screenshot.
- File hint: `ui/theme/Theme.kt` `accentScheme()` overrides only `primary`, `primaryContainer` and `secondary`. Also set `onPrimary`,
  `onPrimaryContainer`, `secondaryContainer`, `onSecondaryContainer` and `tertiary`, or generate the full scheme from the seed. Severity: cosmetic.
