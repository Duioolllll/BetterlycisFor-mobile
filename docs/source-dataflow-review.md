# BetterLyrics v1.3.527.0 source dataflow review

Scope: source-only static analysis of `/workspace/scratch/2baf6da00ccf/BetterLyrics-Release`, Git tag `v1.3.527.0`, commit `3b04c19e371818d820dfc7197789168d70a1543b`. No Windows runtime behavior or binary/source equivalence is established by this report. Parent investigation owns official ZIP/MSIX extraction and REA assembly corroboration.

All paths below are relative to the release repository root. `Core/` abbreviates `src/BetterLyrics.DotNet/BetterLyrics.Core/`; `Win/` abbreviates `src/BetterLyrics.DotNet/BetterLyrics.WinUI3/BetterLyrics.WinUI3/`.

## 1. Media discovery and normalization

`Win/Providers/MediaManagerProvider.cs:43–54` starts `WindowsMediaController.MediaManager` and subsequently subscribes its opened/closed/focused/media/playback/timeline events. A concurrent dictionary wraps each external session with a `MediaSessionProvider`; `SyncMediaSessions` removes closed IDs and constructs providers for new IDs (`:105–120`).

`Win/Providers/MediaSessionProvider.cs:34–57` stores the Windows session ID, reads `TryGetMediaPropertiesAsync()` and copies `Title`, `Artist`, `AlbumTitle`, `Genres`, and thumbnail bytes. Playback state uses `GetPlaybackInfo().PlaybackStatus` (`:65–82`). Timeline uses `GetTimelineProperties().Position` and `EndTime` (`:87–100`). Controls directly forward to Windows transport-control methods (`:104–132`). The release constructor does not initialize playback status; metadata and timeline reads swallow exceptions.

`Core/Implementations/Services/GsmtcService/GsmtcService.cs:375–396` subscribes wrapper events, initializes the manager, records existing session IDs, and chooses a desired session. `GetCurrentDesiredSession` (`:610–664`) has this exact priority:

1. Enabled built-in BetterLyrics session that is playing.
2. Enabled focused session that is playing.
3. First enabled playing session in manager enumeration.
4. Enabled focused session even if paused.
5. Enabled built-in session even if paused.
6. If the music-gallery window is open but no self session is available, return null.
7. Otherwise first enabled session; null if none.

This is selection by current session state and manager enumeration, not a literal sort of every session by drag-and-drop list order.

Media-property change events are debounced for 1000ms (`:468–471`). Core normalization (`:492–599`) does the following:

- Ignores events whose provider object is not reference-equal to `_currentDesiredSession`.
- Resets offset if configured; refreshes session metadata.
- Apple Music artist field is split on ` — `; first part becomes artist, last becomes album; ` - Single` and ` - EP` are removed from album (`:513–519`).
- NetEase/QQ track IDs and linked local filename are extracted from special `Genres` values (`:521–537`).
- LX Music may supply time/cover through a separate SSE server (`:716–806`). Optional Windows memory reader supplies playback time (`:812–832`); it is not a portable prerequisite for ordinary players.
- All title/artist/album empty means placeholder. Otherwise missing individual fields become `N/A`, duration comes from the previously refreshed timeline, and `SongInfo` records player ID, song ID, file link, and start time (`:561–579`).
- Resets play statistics; updates Discord/Last.fm if enabled; requests lyrics and album art (`:581–599`).

Android implication: preserve logical track identity, metadata and timing contract, but replace this adapter with Android media-session APIs. Windows genres hacks, SSE specialization and memory reading are source-specific optional integrations, not capabilities of Android's ordinary session API.

## 2. Search, saved mappings, source priority and retries

`Core/Implementations/Services/LyricsSearchService/LyricsSearchService.cs:65–222` is the central search controller.

Saved manual query mappings are checked first (`:82–111`). The mapping can replace title/artist/album, mark a track instrumental, or force a provider. Instrumental tracks return a musical-note placeholder. A forced provider path returns its result directly, without the usual final score/`IsFound` gate. `SongSearchMapService.cs:61–70` retrieves mappings by exact original title, artist and album; duration/player are not part of mapping identity.

Normal search reads the selected player configuration, chooses enabled lyrics providers, and clones query metadata. In `BestMatch`, all sources are searched concurrently, each must have nonempty raw lyrics and meet its source-specific or global threshold, all searches are awaited, and the highest `MatchPercentage` wins (`:137–175`). In `Sequential`, enabled providers are queried in configured order and the first acceptable result wins (`:177–207`). The score does not reward richer word timestamps. No acceptable result throws, allowing retry.

Defaults: `Core/Models/Settings/MediaSourceProviderInfo.cs:94–105` selects Sequential, threshold 60, and enables every non-internal enum provider. Enum order in `Core/Enums/LyricsProvider.cs:5–18` is QQ, Kugou, NetEase, LRCLIB, AMLL TTML DB, embedded local music lyrics, local LRC, local ESLRC, local TTML, Apple Music. BetterLyrics and LibreTranslate are internal providers; plugin sources receive IDs >=1000 (`Core/Extensions/LyricsProviderExtensions.cs:35–47`).

`GsmtcService.LyricsUpdater.cs:21–81` debounces refreshes, clears the previous search result, displays a loading placeholder, runs search in a background task, and then parses the result. Default retry count is 3 (`GeneralSettings.cs:50`). Backoff between attempts is `(attempt*2-1)*1000`: 1s then 3s. Cancellation exits; exhausted failure becomes the not-found placeholder. Default debounce is 250ms (`Core/Constants/Time.cs:5`). `Debouncer.cs:78–107` cancels a previous execution only when the new debounce has expired and execution begins.

### Provider details

| Source | Release implementation | Relevant source |
|---|---|---|
| QQ | Direct QQ ID when player's family matches, otherwise Lyricify search; obtains QRC/raw plus translated lyrics | Search service `:634–677` |
| NetEase | Direct NetEase ID or Lyricify search; uses `Lrc.Lyric`, `Tlyric.Lyric`, `Romalrc.Lyric`; release path does not consume YRC | `:680–724` |
| Kugou | Lyricify search, hash-based lyric candidates, first candidate, lyric retrieval using candidate ID/access key; KRC parser extracts Chinese translation | `:727–789` |
| LRCLIB | `/api/search` URL with title/artist/album and `durationMs`; takes only array element zero and reads `syncedLyrics`, not `plainLyrics` | `:578–631` |
| AMLL | Downloads/caches a newline-JSON metadata index; selects by matching NetEase ID or highest metadata score, then downloads referenced TTML; reverse lookup may obtain duration | `:490–575`, `:283–324` |
| Local lyrics | Searches enabled, indexed media folders; extension-specific best metadata match; uses stored `EmbeddedLyrics` rather than reopening the file at search time | `:383–439` |
| Embedded music lyrics | Searches indexed audio files with embedded lyrics; keeps best matching file | `:441–488` |
| Apple Music | Own provider initializes access token and optional stored media-user-token, language/storefront, then retrieves TTML | `:792–795`; `Providers/AppleMusic.cs:40–60,156–242` |
| Plugins | `ILyricsSource.GetLyricsAsync(title,artist,album,duration,token)`, then ordinary metadata score | `:798–833` |

LRCLIB candidate iteration, proper `/api/get` duration queries, plain-lyrics fallback, network timeouts, or YRC support in a mobile implementation are improvements/additions, not literal reproduction of the release provider.

## 3. Matching and caching

`Core/Helpers/MetadataComparer.cs:10–16,28–69` uses Jaro–Winkler string similarity. Title weight .30, artist .30, album .10, duration .30. Strings are trimmed/lowercased; both empty means similarity 1; one empty means 0 (`:128–136`). Durations within 1 second score 1, at least 10 seconds apart score 0, linearly interpolated between; absent/zero remote duration scores 0 (`:151–167`). Weights are not dynamically renormalized when metadata is absent.

When either title is absent, fallback compares fingerprints consisting of lowercased words with Unicode punctuation/symbols removed and tokens sorted. It uses linked filename when source metadata is absent (`:50–66,170–185`). The array overload takes maximum similarity across alternative titles/artists/albums (`:72–125`).

`Core/Extensions/SongInfoExtensions.cs:61–75` hashes trimmed title, artists, album and rounded duration in seconds as `title|artists|album|seconds` using SHA-256. This does not lowercase or sort artists. Cache identity additionally includes provider (`LyricsCacheService.cs:28–36`). Local providers bypass cache (`LyricsProviderExtensions.cs:26–37`). Cached results return before searching; fresh cacheable results are saved regardless of whether lyrics were found (`Search service :334–341,:378`).

`LyricsCacheService.cs` has no TTL. Empty results can therefore persist until cache bypass/clear. On update of an existing item, raw lyrics/translation/metadata are copied, but `Transliteration` itself is omitted (`:54–68`), even though initial clone copies it (`LyricsCacheItem.cs:79–98`). This is a static source finding, not an observed user-visible bug.

## 4. Raw formats and lyric data contract

`Core/Helpers/Lyrics/ContentParser/LyricsContentParser.cs:38–76` detects the raw format and dispatches LRC/ESLRC, QRC, KRC or TTML. It then loads source translation/phonetics, optionally generates missing tracks, fills syllable ends, and fills line ends. Main `LyricsData` contains `LyricsLine` entries with original text, secondary translation, tertiary phonetic text, absolute timing and syllable timing. `AgentId` is retained for TTML voices. `IsPrimaryHasRealSyllableInfo` distinguishes genuine word timing from generated estimates; `LyricsData.IsWordByWord` checks whether any line has this flag (`Models/Lyrics/LyricsData.cs:18–35`).

### LRC / ESLRC

`LyricsContentParser.Lrc.cs:9–78` recognizes `[mm:ss.xx]` and word stamps with square/angle brackets. More than one matching timestamp segment is treated as genuine syllable timing. Otherwise it produces a line using the first square-bracket timestamp. It converts fractional milliseconds using right padding. Rows are grouped by start time, sorted, and multiple rows with the same timestamp become separate language tracks (`:82–123`). It does not implement a general plain-text fallback, `[offset:]`, or standards-complete repeated line-tag semantics.

### QRC / KRC

The external Lyricify helper decrypts/parses these structures. `LyricsContentParser.QrcKrc.cs:10–63` maps `ILineInfo` and `SyllableLineInfo` into original lines and true syllable timing, retaining each segment's absolute start/end. These are required for QQ/Kugou word highlighting; fetching only their ordinary LRC does not preserve equivalent fidelity.

### TTML

`LyricsContentParser.Ttml.cs` supports substantially more than `<p>` plus timed text spans:

- Skips documents whose timing attribute is `None` (`:31–35`).
- Apple Music `<head>` translation/transliteration tracks are indexed by `for`, XML language and `itunes:key` (`:37–75,:101–147`).
- Original paragraph `ttm:agent` identity is copied (`:83–95,:313–322`).
- `x-bg` background vocals become separate original lines; `x-translation` and `x-roman` become auxiliary tracks and are not concatenated into the original lyric (`:150–161,:253–255,:324–353`).
- Ruby container/base/text constructs are handled for Japanese display/timing (`:257–275`).
- Explicit whitespace/punctuation between timed spans is appended to the preceding syllable, while XML indentation/newline whitespace is trimmed (`:233–249`).
- Missing container timing is inferred from first/last syllable; span timing falls back to container values (`:218–224,:279–311`).
- Translation/phonetic tracks are filtered by requested language and source language (`:164–201`, `LyricsParseRule.cs:38–75`).
- Time parser supports seconds (optional suffix `s`), `mm:ss.xxx`, `hh:mm:ss.xxx`; unknown formats return zero (`:369–412`). Full TTML `dur`/ticks/frames/milliseconds-suffix support is not present in this release implementation.

A parser that merges every identical start into a translation loses overlapping original voices/background vocals. A port data contract should preserve independent tracks and `AgentId` instead.

### Timestamp fallback

`LyricsContentParser.cs:391–460` fills absent syllable ends from the next syllable; the last syllable defaults to min(start+1s,next-line-start), except overlapping/out-of-order cases use +1s. Lines without genuine syllables receive evenly distributed per-UTF-16-character estimated timing over their estimated line interval. The flag remains false. `EnsureEndMs` then derives line end from the last syllable (`:360–385`). Genuine and estimated timestamps must remain distinguishable in the port.

## 5. Translation, phonetics, display attachment

Intrinsic translations from QQ/Kugou/NetEase and intrinsic NetEase romanization are parsed as LRC (`LyricsContentParser.cs:189–219`). TTML may embed both types directly. Original language is detected lazily from its text (`Models/Lyrics/LyricsData.cs:20–23`).

If translation is enabled, source differs from target, no matching translation track exists, and LibreTranslate is enabled, the complete original lyric text is posted to `<configured server>/translate` with q/source/target fields (`LyricsContentParser.cs:264–307`; `Implementations/Services/TranslateService.cs:23–46`). Result lines inherit original timing. This is network/self-hosted translation, not inherently an offline in-app model. The plugin-translator branch in `TranslationService` is commented out (`:48–64`).

Romanization generation first tries enabled loaded `ILyricsTransliterator` plugins until nonempty output (`TransliterationService.cs:19–42`); if no output, built-in generation exists only for Mandarin pinyin and Cantonese Jyutping (`LyricsContentParser.cs:223–261,310–354`). Japanese/Korean settings enable accepting/generated plugin tracks but do not themselves provide built-in transliterators. Existing intrinsic phonetic text may still be displayed.

Final `ParseAsync` filters information lines, chooses an appropriate phonetic track and longest matching translation track, and optionally converts simplified/traditional Chinese (`LyricsContentParser.cs:96–186`). Both translation and phonetic text attach to original lines by the first start within ±50ms (`Core/Extensions/LyricsDataExtensions.cs:39–69`).

## 6. Confirmed source risks and port decisions

These are source-observed risks or behavior limitations, not runtime-reproduced crashes:

1. Initial session state can remain Stopped: wrapper starts before subscribing and constructor does not read current playback state. Later dev fixes this by reading status in its constructor. Initialize Android controllers synchronously from current state before relying on callbacks.
2. Release compares session provider references, while the wrapper can recreate provider instances. Later dev introduces `IsCurrentDesiredSession` comparing stable `SessionId`. Android implementation should use stable token/session identity and unregister old callbacks.
3. Repeated media refresh adds the same memory-reader event handler without removing it (`GsmtcService.cs:540–548`); avoid subscription accumulation in the mobile adapter.
4. Lyrics debounce cancellation is cooperative. In-flight metadata refresh is launched through an Action and is not canceled. The source lacks a final track-generation check before assigning fully parsed results. Add track identity/generation checks so fast switching cannot commit stale lyrics; describe this as a stability improvement.
5. Cached misses have no expiry; cache updates omit intrinsic transliteration. Do not reproduce these defects accidentally.
6. BestMatch waits for every provider and selects metadata score, not timestamp quality. A progressive result UI or word-sync preference is a distinct design change.
7. LRC handling is narrow; TTML handling is rich. Explicitly document any mobile format subset instead of claiming complete equivalence.

## 7. Version differences from current dev inspected by parent

Compared against `/workspace/scratch/2baf6da00ccf/BetterLyrics` HEAD `0d3e6e26d9e6cb217ead7c6cf5ff1d85de1a3f2f`:

- Release desired-session priority gives playing built-in player absolute priority. Dev prefers valid focused playing, then current playing, then another playing session; preserves stable current identity.
- Dev initializes `MediaSessionProvider.PlaybackStatus` in the constructor and adds `IsCurrentDesiredSession` based on ID.
- Dev uses typed playback messages rather than generic property-changed messages and separates `UpdateLyricsPresentation` from full search. It reparses the existing result for translation/phonetic display changes and checks that the result object is still current before assignment.
- TTML/parser edits are relatively small, but renderer/animation/layout changes are substantial. Keep release-source claims separate from dev-source claims.

## 8. Suggested focused REA corroboration anchors

For the extracted release's `BetterLyrics.Core.dll`:

- `BetterLyrics.Core.Helpers.MetadataComparer.CalculateScore`: .30/.30/.10/.30 constants; `GetDurationSimilarity` 1s/10s thresholds.
- `BetterLyrics.Core.Implementations.Services.LyricsSearchService.LyricsSearchService.SearchSmartlyAsync` and generated MoveNext: branches on BestMatch/Sequential, Task.WhenAll, score ordering.
- `...SearchLrcLibAsync` generated MoveNext: `https://lrclib.net/api/search?`, `durationMs`, `syncedLyrics`, first-array-item access.
- `BetterLyrics.Core.Extensions.SongInfoExtensions.GetCacheKey`: SHA256 and title|artists|album|rounded-duration concatenation (compiler-generated extension class names may differ).
- `...GsmtcService.GetCurrentDesiredSession`: built-in/focused/playing fallback order.
- `...LyricsContentParser.ParseTtml` / `ParseTtmlSegment`: namespace/role strings `x-bg`, `x-translation`, `x-roman`, `ruby`.
- `...LyricsCacheService.SaveLyricsAsync`: raw/translation assignments and missing Transliteration assignment on update.

Async C# methods compile into state-machine `MoveNext` bodies. A shallow async entry-point inspection alone does not establish the full algorithm. Corroborate both declared members and their state-machine bodies where available.

## 9. Shipped helper follow-up

The original package's `Lyricify.Lyrics.Helper.dll` has now been classified with REA and 23 selected types decompiled with ILSpy without running target code. See `/workspace/scratch/2baf6da00ccf/original-package/helper-review/HELPER_ANALYSIS.md` and its decompilation manifest. Confirmed helper assembly is 0.1.4.0, .NET Standard 2.1, AnyCPU CIL, SHA256 `33543eb8d9d63c16b6137cefeb069499b98f49a27b77730222ebb0c2f1f8b57c`.

Important corroboration: the application uses QQ's encrypted verbatim lyric-download path, NetEase's encrypted WEAPI lyric POST, and Kugou's decoded KRC path. Ordinary LRC request alternatives would be lower-fidelity source substitutions. QRC word time fields are absolute; KRC word offsets are relative to each line. The helper supports EAPI/YRC methods that the application does not call. These distinctions are now grounded in shipped assembly decompilation, not inferred from method names alone.
