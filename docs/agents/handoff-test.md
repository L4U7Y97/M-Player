# Tester handoff

## Round 1 (2026-10-26)

Scope: JVM unit tests (JUnit 4) for the pure classes in spec §2, written against `docs/intent.md`, spec §4/§6/§7
and the API in `handoff-dev.md` "Round 1". Nothing under `app/src/main` was touched.

### Command

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat :app:testDebugUnitTest --console=plain
```
Result: **BUILD SUCCESSFUL. 136 tests, 136 passed, 0 failed, 0 ignored.**
Reports: `app/build/test-results/testDebugUnitTest/*.xml`, `app/build/reports/tests/testDebugUnitTest/index.html`.

### Tests per class (`app/src/test/java/dev/localmusic/`)

| Test file | Tests | Covers |
|---|---|---|
| `data/scan/ScanRulesTest.kt` | 18 | the 8 supported extensions, case-insensitive ext, unsupported (jpg/txt/m3u/mp4/wma/`a.mp3.bak`), hidden files and dirs, no ext/trailing dot/blank, `shouldDescend` root always + subfolders only when included, `childPath`, plus a simulated BFS walk over an in-memory tree with subfolders on and off (T7 AC) |
| `data/scan/TagFallbacksTest.kt` | 18 | all tags trimmed; no tags → title = file name without ext, "Unknown artist", album = parent folder; artist → albumArtist; blank parent → "Unknown album"; `parseNumber` ("3", "3/12", " 03 ", garbage/0/negative), `parseYear`, `parseDuration` |
| `domain/TrackSorterTest.kt` | 23 | every field asc/desc; null track numbers last in both directions; disc-then-track with null disc = 1; ties → fileName asc in both directions; case-insensitive title and file name; Long extremes; stability; input not mutated; works on `Track`; `comparator` == `sort`; `Track.albumGroupArtist` |
| `domain/QueueModelTest.kt` | 31 | move: current item moved, items moved across current (onto the current index too), unaffected cases, exhaustive 5×5×5 check that current still points at the same item, out of range / same index / empty; remove: before, after, current first/middle/last (last wraps to 0), only item → -1, out of range, remove until empty; append: empty queue, negative current, empty newItems, duplicates; `playOrder`/`upNext` linear, shuffled, invalid permutation, count ≤ 0 |
| `domain/RepeatCyclerTest.kt` | 7 | OFF→ALL→ONE→OFF, 3-cycle identity, Media3 int constants, int round trip, unknown → OFF, `nextPlayerMode` 0→2→1→0, labels match the spec §11 QA strings |
| `domain/PlaylistOrderingTest.kt` | 18 | move up/down/first↔last, entryIds kept, out-of-range/same → no-op but renumbered, list order wins over the `position` field; remove middle/first/last/only/out-of-range/duplicate; append to empty/existing (entryId 0)/duplicates/nothing; `normalize` (stable), `renumber`; Move up/Move down button sequence with edge no-ops |
| `settings/SettingsMapperTest.kt` | 21 | `AppSettings`/`EqSettings` defaults match spec §7; accent labels; enum round trip for ThemeMode/Accent/SortField; case-insensitive and trimmed parsing; null/blank/unknown → default; CSV format and round trip; invalid entries → 0; clamping (including when 0 is out of range and with reversed bounds); `bandCount` truncates or pads (including 0 and negative); strength 0..1000; preset index < -1 → -1; full AppSettings round trip through stored strings; missing stored values → defaults |

### Bugs filed

None. All tests passed against spec + intent on the first run, so `docs/agents/bugs.md` was not created.

### Spec ambiguities (not filed as bugs; the tests pin the current behaviour where noted)

1. **Title/file-name sort is lexical, not natural.** `CASE_INSENSITIVE_ORDER` puts "Track 10" before "Track 2" and
   accented letters ("Élan") after "Z". Spec §6 asks for exactly this, but users may expect natural/locale order
   (`java.text.Collator`). Not pinned by a test, so it can be changed freely.
2. **TRACK_NUMBER descending also reverses the disc order** (disc 2 tracks come before disc 1). This follows from
   "descending reverses the primary key". Pinned in `trackNumber_discBeforeTrack_nullDiscCountsAsOne`.
3. **Removing the current queue item when it is last** → current index 0. This mirrors ExoPlayer (it moves to the first
   item and ENDs), so the queue UI must not show item 0 as "playing". Pinned in `remove_currentLast_wrapsToFirst`.
4. **Playlist "insert"**: there is no insert-at-index operation. New entries are appended only (spec §6 lists append).
   If "Add to playlist" ever needs a position, a new API is needed.
5. **Hidden-file rule** is dot-prefix only. A `.nomedia` file does not exclude its folder; intent/spec don't say whether it should.
6. **`parseYear`** takes the first 4-digit run anywhere, so `"12345"` → 1234 and `"Remaster 2011 of 1975"` → 2011. That is fine for
   real tags, but the spec only says "first 4 digits".
7. **Null track numbers ignore disc** (a disc-2 track without a number sorts among all other unnumbered tracks by file
   name). This follows the dev handoff; the spec is silent.
8. File names with trailing whitespace (`"a.mp3 "`) are not treated as audio. SAF names normally have no trailing whitespace. Not pinned.

### Not covered (not pure / out of scope this round)
`FolderScanner`, `TagReader`, `LibraryRepository`, Room DAOs, `SettingsRepository` (DataStore). The T7/T8/T20 ACs
(nested scan on/off on a device, corrupt file, missing marking, permission release) need QA on the emulator.
