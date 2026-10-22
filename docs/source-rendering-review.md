# BetterLyrics v1.3.527.0: rendering, synchronization and mobile portability review

Scope: static source review of `/workspace/scratch/2baf6da00ccf/BetterLyrics-Release`, tag `v1.3.527.0`, commit `3b04c19e371818d820dfc7197789168d70a1543b`. All paths below are relative to `src/BetterLyrics.DotNet/`. No Windows runtime behavior was observed in this review, and no package/DLL was decompiled by this worker. Parent investigation owns package evidence. There were no applicable AGENTS.md files at the repository root or its checked ancestors.

## Findings that should affect the port before implementation resumes

1. A line may be the scroll anchor while several other lines are simultaneously active. Preserve `start`, `end`, `agent`, real/synthetic syllable provenance, and auxiliary tracks in the mobile model.
2. Actual syllable timestamps and animation are independent. Synthetic timings for line-only LRC must not be presented as accurate word synchronization. Original Auto mode disables progressive word effects for synthetic timing.
3. TTML is a multi-track, multi-agent format in this app. A simple `p`/`span` text flattener drops backing vocals, Apple head translations, romanization and ruby timing.
4. Original highlight progress is based on measured glyph-region widths and timestamps, not character count alone. Wrapping and long-note effects depend on per-syllable/per-character geometry.
5. The renderer's local clock advances using frame elapsed time and corrects against media-session positions. An Android port should use monotonic playback-state timestamps, playback speed and the reported position as its authority, while retaining independent display smoothing.
6. The Windows GUI, graphics, WASAPI audio capture, taskbar/AppBar/wallpaper hosting and plugin loading have distinct platform boundaries. Source-level portability of parsers does not make the WinUI UI portable.

## 1. Original update/draw pipeline

`BetterLyrics.WinUI3/BetterLyrics.WinUI3/Controls/NowPlayingCanvas.xaml:13-19` hosts one `CanvasAnimatedControl`, with independently bound CreateResources, Update and Draw callbacks. Frame target comes from user `LyricsWindowStatus.FPS`.

`Controls/NowPlayingCanvas.xaml.cs:716-830` performs, per update:

- Advance accent/environment color transitions and local playback state.
- Rebuild lyric models/layout only when dirty flags indicate changed lyrics/layout.
- Ask `LyricsSynchronizer` for a primary line index.
- Calculate the target scroll offset and ease scroll to it; a layout change jumps to the new target.
- Recompute hover/visible ranges and update line/character animation for the visible range, or all lines during user scrolling.

The Draw pass is separate (`Controls/NowPlayingCanvas.xaml.cs:594-654`). The actual layer order is flat overlay, album-cover background, fluid background, spectrum, snow, fog, rain, then lyrics (`1086-1129`). Optional edge feathering wraps composition (`1132-1144`). Spout output is an optional Windows texture path (`631-647`).

`TriggerRelayout` (`1172-1231`) disposes old render models when lyrics change, converts parsed `LyricsLine` values to GPU-facing `RenderLyricsLine` objects, extends render-only long-note tails, assigns lanes/alignments, then measures and arranges. Layout and source-timing changes are separate concerns.

Android mapping: preserve a frame update/draw boundary, dirty layout flags, per-line animation state and culling. Canvas/Compose or a carefully built WebView renderer can implement this, but a collection of fixed CSS active/inactive classes is a reduced implementation rather than the same animation engine.

## 2. Clock, correction, offsets, pause and seek

- `Controls/NowPlayingCanvas.xaml.cs:1245-1253`: while `CurrentIsPlaying`, `_songPosition += elapsedTime`; `_songPositionWithOffset` equals that value plus provider `PositionOffset`.
- `484-499`: reported media position corrects `_songPosition` only when the difference reaches `TimelineSyncThreshold`. A difference of threshold + 5000 ms requests relayout.
- `456-460,1256-1259`: changing `CurrentSongInfo` resets only `_songPosition` to zero.
- `BetterLyrics.Core/Models/Settings/MediaSourceProviderInfo.cs:24-37`: normal providers default to 300 ms threshold and zero offset. Apple Music defaults to 1000 ms threshold and +1000 ms offset.
- The displayed active-line lookup and animator use `_songPositionWithOffset` (`749-751,824`). A lyric click forwards the line's original `StartMs` through `GsmtcService.ChangeLyricsLineAsync`/`ChangePositionAsync` (`BetterLyrics.Core/Implementations/Services/GsmtcService/GsmtcService.cs:181-189`).

Static risks to fix deliberately on Android, rather than reproduce blindly:

- `_songPositionWithOffset` is recomputed only inside the playing branch. A paused seek or changing offset while paused can leave the render clock stale until playback resumes. The position callback updates `_songPosition`, not the offset-adjusted clock. This is a source-derived risk, not an observed runtime reproduction.
- Local time advances at 1x, without reading a playback speed multiplier in this renderer. Android media states can expose other speeds, so the mobile model must retain and apply them.
- Seeking to a clicked displayed line uses raw source start time without compensating a display offset. Preserve the distinction between media time and lyric display time and define user-facing offset semantics.
- A song swap should reset both clocks and invalidate any prior synchronization anchor. The source resets only the base clock in this method.

## 3. Scroll anchor versus simultaneous playing lines

`BetterLyrics.Core/Helpers/Lyrics/LyricsSynchronizer.cs:14-66` caches the last anchor index, tries lane zero first, then the lowest lane candidate. Its interval predicate is `start <= time < nextLine.start`; it does not consult the line's own end timestamp. This is the algorithm as written, so do not describe it as a full interval-overlap solver.

In contrast, `BetterLyrics.Core/Models/Lyrics/BaseRenderLyrics.cs:15-22` uses `[StartMs, EndMs)` to determine whether an individual line/syllable/character is active; play progress is clamped `(time-start)/duration`. `LyricsAnimator.cs:100-106` and `LyricsRenderer.cs:137-142` invoke this predicate independently for every visible line. Several lines may therefore have active visual effects even though one index drives auto scroll.

`Helpers/Lyrics/LyricsLayoutStrategy/LyricsLayoutStrategyBase.cs:36-62` assigns the earliest available lane based on end time and a 50 ms overlap tolerance. `65-95` assigns singer-based alignments: first distinct agent left, second right, additional agents alternate, and `v1000`/group agents center.

Port design: retain overlapping original lines rather than merging every equal timestamp as a translation. Make an explicit scroll-anchor function and a separate active-line set. If the mobile implementation corrects the source's next-start predicate for overlap handling, record that as an intentional improvement.

## 4. Parsed timing, synthetic syllables and TTML semantics

`BetterLyrics.Core/Models/Lyrics/LyricsLine.cs:33-49` holds primary/secondary/tertiary syllables and text, `IsPrimaryHasRealSyllableInfo`, and `AgentId`. The real timing flag is semantically significant.

`ContentParser/LyricsContentParser.cs:391-457` fills missing ends for real syllables using the next syllable. A final syllable without an end gets at most 1 second bounded by the next line, unless it starts beyond the next line, when it gets 1 second. For line-only lyrics, it divides the estimated entire line duration equally by UTF-16 text length, generating synthetic per-character syllables. `EnsureEndMs` then derives a missing line end from its last syllable (`360-383`).

`LyricsAnimator.cs:82-88` and `Renderer/LyricsRenderer/HorizontalLyricsLineRenderer.cs:25-29` implement three modes: Auto requires real timing; Always permits synthetic effects; Never fills the whole active line. Thus line-only LRC has animation data internally but is not accurately word timed. Preserve the flag rather than inferring accuracy merely from a nonempty word array.

`ContentParser/LyricsContentParser.Ttml.cs` supports considerably more than plain paragraphs:

| Source range | Actual handling |
|---|---|
| 29-35 | Preserve XML whitespace; skip an Apple document with timing `None`. |
| 37-75,101-147 | Collect head `translation`/`transliteration` records by `for` key and language, then attach to paragraph `itunes:key`. |
| 85-95 | Preserve paragraph `ttm:agent`. |
| 150-161,253-255 | Exclude `x-bg` from main text and parse it as its own original line, with fallback parent bounds. |
| 239-249 | Ignore formatting newlines/indentation, while appending actual intervening punctuation/spaces to the previous syllable so character indices remain aligned. |
| 262-275 | Support `tts:ruby="container"`, the ruby base text, and timing from ruby text spans. |
| 279-285 | Preserve span text including explicit trailing spaces, using span begin/end with parent fallback. |
| 309-321 | Derive a missing container bound from syllables and mark real syllable timing. |
| 324-365 | Parse inline `x-translation` and `x-roman` into language-specific auxiliary tracks. |
| 167-200 | Filter translation/romanization tracks according to enabled language rules. |
| 369-412 | Parse hours/minutes/seconds or seconds with optional `s` suffix; malformed values return zero. |

The parser uses absolute millisecond bounds; general TTML inherited timing, frame-rate timing and arbitrary `dur` semantics are not implemented in this release routine. A broader parser can improve support, but should not claim that original source supports all TTML features.

Further static limitations: TTML original lines are appended in paragraph/background order and are not sorted in this parser or `LyricsData` constructor (`Models/Lyrics/LyricsData.cs:13-18`). Later synchronizer/lane logic assumes a useful order. Sorting in the port is an intentional normalization, but equal-time backing lines must remain separate. Translation/romanization is joined to original starts using a 50 ms tolerance (`Extensions/LyricsDataExtensions.cs:39-69`), so blindly joining by list index is a behavior change.

## 5. Geometry, highlighting and long-note effects

`Models/Lyrics/RenderLyricsLine.cs:56-128` creates separate text layouts for primary text, translation and phonetics, with CJK/Western font selection, wrapping, text direction and alignment. `PrimaryTextLayout.GetCharacterRegions` supplies actual glyph geometry (`110`).

`154-190` associates each UTF-16 character index with its parent syllable and divides that syllable's duration across its characters. The actual primary word timestamp remains the parent; character fill is a visual interpolation. A mobile renderer needs shaped text geometry, particularly for Korean, Japanese, ligatures, emoji, Arabic and line wrapping. Splitting arbitrary Unicode strings into independent JavaScript character spans can change shaping.

`HorizontalLyricsLineRenderer.cs:18-51` calculates highlighted width as the sum of completed glyph widths plus current glyph width times current progress; the output gradient runs left to right (`54-69`). `LyricsLineRendererBase.cs:107-220` combines the played/unplayed fill and stroke gradients; `237-265` applies character float/scale and clips glow to the already played portion. Normal lines use cached masks (`RenderLyricsLine.cs:194-248`), then tint them rather than rebuilding complete text every frame.

`LyricsAnimator.cs` details:

- 48-60,114-145: distance above/below the primary line is normalized by available viewport space and controls duration/delay independently for the two directions.
- 148-225: blur, scale, alpha, fill/stroke colors and optional fan angle animate toward distance-dependent targets. Non-current scale can fall from 1.0 toward 0.75; blur can reach 5.
- 239-309: optional glyph float, glow from line start and restoration after seek/passed timestamps.
- 311-349: long syllables can trigger scale and glow, with duration-limited in/out keyframes for every child glyph.
- 356-400: no-wrap marquee uses actual text bounds and line play progress.
- `Models/Settings/LyricsEffectSettings.cs:44-76`: long-note defaults are 700 ms, scale target 115%; default scroll duration 500 ms and ease-out quadratic (`9-20,96-120`).

`Controls/NowPlayingCanvas.xaml.cs:1321-1358` extends render-line end times to let a long-note animation settle; source timing is otherwise retained. This extension is presentation behavior. Keep transport seeking and lyric data tied to original time rather than persisting animation tails as corrected lyrics.

`BetterLyrics.Core/Helpers/ValueTransition.cs:129-245` implements an interruptible queue of keyframes, optional start delay, explicit per-step or evenly distributed total durations, and elapsed-time overflow across multiple keyframes. Starting a new transition begins from the current rendered value. An Android replacement should avoid restarting animations from fixed zero on every media callback.

Two additional static risks should inform the rewrite. `Extensions/RenderLyricsSyllableExtensions.cs:14-17` creates a new `BaseRenderLyricsSyllable` each time a render line is projected into the core animator; its constructor copies timing/text but does not copy `IsPlayingLastFrame`. Since `ToBaseRenderLyricsLine` is called during every frame update, the syllable-change guard in `LyricsAnimator.cs:311-349` can be rearmed repeatedly for an already active syllable. Preserve stable animation identities in the port instead of copying this wrapper behavior. Also, the integer `syllable.DurationMs / syllable.Length` in `RenderLyricsLine.cs:176` can be zero for a short syllable; `BaseRenderLyrics.GetPlayProgress` has no zero-duration guard. Clamp/guard degenerate durations without inventing real timing precision. Neither issue was runtime-reproduced in this review.

## 6. Layout, touch and resources

`HorizontalLyricsLayoutStrategy.cs:41-94` chooses responsive or fixed font sizes, then measures layout with actual width/height. It orders the three visible lyric layers according to configuration and adds inter-layer/line spacing (`101-144`). Alignment affects the line's center of scaling/rotation (`146-201`). The primary scroll target is minus the current line's geometric center (`211-217`), combined with the user-configured viewport anchor by the renderer (`LyricsRenderer.cs:101-108`).

`Controls/NowPlayingCanvas.xaml.cs:1011-1025` clicks a hovered lyric to seek. Wheel scrolling creates a separate user scroll offset and returns to auto follow after a 3000 ms idle debounce (`1027-1071`). The analogous touch design needs to distinguish a tap from a drag, suspend auto-follow during exploration, and offer/automate return to current lyrics.

On unload, the control unsubscribes messenger and animation callbacks, pauses/removes the canvas, disposes renderers/line caches/audio capture and closes texture output (`947-981`). Android lifecycle needs equivalent cleanup for session callbacks, handler/choreographer loops, cover bitmaps, overlay windows and request generations.

## 7. Background/effects and portability boundaries

`Renderer/FluidBackgroundRenderer.cs:49-76` drives a four-color pixel shader with accumulated time times speed, optional static caching, bass breathing and parallax. `Shaders/FluidBackgroundEffect.cs:150-203` is a procedural noise-rotated, sine-warped four-color gradient with RGB/HSV blending, optional light wave and dithering. It is not a fluid physics simulation. The shader can be translated to GLSL/AGSL/WebGL while preserving its math; a static blurred cover is a reduced visual substitute.

`CoverBackgroundRenderer.cs` handles cover crossfade and blurred/rotating cached layers. `Core/Helpers/SpectrumAnalyzer.cs:159-185,290-367` captures Windows WASAPI loopback, normalizes stereo samples, applies a Hamming window and FFT; bass energy drives breathing. Android cross-app audio capture is a different permission/platform problem, so do not display decorative random bars as measured music spectrum.

Project layers:

| Layer | Source evidence | Mobile treatment |
|---|---|---|
| Core models/algorithms | `BetterLyrics.Core.csproj:4-8` targets .NET 10 plus Windows; parsers, synchronizer, animator use mostly neutral types. | Port semantics/models or isolate reusable pure .NET code. Core as a whole also references NAudio and desktop integrations, so it is not a ready-to-run Android library. |
| Provider interfaces | Core has `IMediaManagerProvider`, `IMediaSessionProvider`, platform/window interfaces. | Implement native Android media-session/lifecycle adapters rather than WinRT wrappers. |
| WinUI graphics | CanvasAnimatedControl, CanvasTextLayout, CanvasGeometry, Direct2D effects, ComputeSharp. | New rendering backend with matching geometry/animation behavior. |
| Desktop hosting | `Providers/WindowManagerProvider.cs:287-345,400-518` calls HWND styles, AppBar APIs and WorkerW hooks. | Fullscreen Activity, optional Android overlay or wallpaper-specific service. Taskbar/AppBar cannot transfer as-is. |
| Plugin SDK | `.NET` assemblies and Windows-specific plugin context; SDK targets .NET 10/Windows (`BetterLyrics.Sdk.csproj:4-14`). | Define compatible capabilities through an Android architecture. Existing .blp/DLL loading is not implemented by a Java APK. |
| Audio visualization | WASAPI loopback in Core helper, CPU FFT and graphics backend. | Separate Android audio-capture implementation if permitted by source apps; otherwise clearly leave this feature unavailable. |

## Recommended verification matrix after the source/package investigation

- Real timed syllable LRC/TTML, line-only LRC in Auto versus Always, missing syllable ends, very short/zero-duration tokens.
- TTML backing vocals overlapping main vocal; same-time lines with distinct agents; head keyed translations; ruby timing; multiple translation languages; formatting whitespace and explicit spaces.
- Play, pause, seek while paused, seek backward/forward, position corrections below/above threshold, non-1x playback, track change, offset change while paused.
- Rotation/window resize/font-size change, wrapped lyric, CJK/Latin/emoji/Arabic text, scrolling exploration then return-to-current, final lyric/long-note animation.
- App background/foreground, revoking permission, session removal, media app switch, disabled/unknown duration and no lyric results.

These are source-informed tests, not tests already executed by this worker.
