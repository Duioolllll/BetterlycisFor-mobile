# Public repository preparation — 2026-10-22

- The public checkout reran 176 offline assertions: ParserTests 79, TextPolicyTests 19, MathTests 28, AudioSignalTests 29, RepositoryRegressionCheck 21. All passed.
- Application source and assets are byte-identical to the existing 0.3.0 source. No application behavior changed for repository publication. Browser suites were not rerun for documentation and packaging changes.
- The existing 103,221-byte APK was rechecked: SHA-256 951c239e45fc4735b4112403f96370cec22506b2003d1daa38ce5c429b1e60ca; v2/v3 signatures pass; packaged web/license assets match the public source.
- Korean, English and Simplified Chinese installation guides have valid local links. Signing keys, private source archives, build outputs and QA screenshots are excluded from publication. The downloadable APK contains no private signing key; fresh source builds generate their own certificate.
- No real-device installation or Play Protect approval is established by these checks.

# Validation record — 0.3.0

- UI browser suite: 29 groups at 360×740, 412×915, 812×375, 1280×800 and 800×1280; flow suite: 9 groups; new player suite: 7 groups covering both views at all five sizes. No JavaScript errors.
- Phone and wide screenshots were compared against the official Apple references in APPLE-UI-REFERENCE.md. Korean glyph rendering was checked with a Noto Sans KR font installed only in the QA environment. The APK uses Android system fonts.
- New controls verified through a mock native bridge: media volume command, volume-only updates preserving the playback clock, periodic hardware-volume updates, disabled fixed-volume state, remaining-time preview, menu import/overlay/search, visible focus return, source selection, transport and sound connection. Both lyric-view controls work; follow resumes after returning from album view. Album/metadata/controls fit the five screen sizes.
- Flow checks retain continuous progress, pause, forward/backward seek, preview/cancel/commit, stale sample guard, acknowledgement/rejection recovery, offset-aware lyric taps, manual reading, song changes, instrumental hold, untimed text and reduced motion. Tests wait for the requested rendered state rather than assuming a software-rendered frame deadline.
- API 36 Java/D8/resources/packaging and v2/v3 signatures pass. io.betterlyrics.mobile, versionCode 7 / 0.3.0. Same development certificate SHA-256 5e017fe5163897e92ca83aa9a8b921ade3ccf065b2a6d53071c4369da74b8010. Bundled assets and the MIT icon license were byte-compared to source.
- Native AudioManager calls were checked against Android's official API documentation and compiled. No device/emulator is attached: actual MediaSession control, hardware volume policy, Bluetooth/remote routing, permission lifecycle and GPU/battery behavior remain device validation. Earlier 176 Java/repository assertions were not rerun for this change.

Android API reference: https://developer.android.com/reference/android/media/AudioManager

## Previous 0.2.2 verification

# Validation record — 0.2.2

- Existing actual-frontend browser suite: 29 functional groups across 360×740, 412×915, 812×375, 1280×800 and 800×1280. The new flow suite adds 9 scenario groups on 412×915 / 1280×800 and a reduced-motion context. No JavaScript errors.
- A native bridge mock verifies continuous movement without extra ticks, progress inside the same verse, pause, backwards seek, slider preview without issuing requests, word-timing preview, cancellation, commit, delayed old samples, acknowledgement, rejected-seek recovery and pause while awaiting acknowledgement.
- Per-song timing correction is included in lyric-tap seeks. Manual scrolling suspends follow; returning to current lyrics works after a drag released outside the lyric viewport. Changing songs cancels a slider preview. Long instrumentals hold; untimed text does not receive a synthetic timeline; a long final verse scrolls through its wrapped rows. Reduced motion holds inside a verse and still follows new verses.
- Source timing, word-mode choice, audio connection and background modes are retained. No lyric provider, native audio capture or permission changes in this release. Java changes are version/User-Agent strings only. The 176 Java/repository assertions recorded for 0.2.0 were not rerun for the frontend change.
- API 36 compile/D8/package and v2/v3 signature checks pass. io.betterlyrics.mobile / versionCode 6 / 0.2.2. Existing development certificate SHA-256: 5e017fe5163897e92ca83aa9a8b921ade3ccf065b2a6d53071c4369da74b8010.
- Browser screenshots reviewed internally, not distributed. No Android hardware/emulator is attached; real music-app seek latency and native playback integration still need device validation.

## Previous 0.2.1 verification

# Validation record — 0.2.1

- Chrome headless with actual packaged frontend source and a mock Android bridge: 29 functional groups; 360×740, 412×915, 812×375, 1280×800 and 800×1280. No page errors or horizontal overflow; metadata and player remain separate. Wide layouts put lyrics beside the player.
- Korean lyric text at font settings 22 and 46 stays inside the lyric viewport. Tapping a line sends the original timestamp to native transport. Manual scrolling makes other lines sharp, and Follow returns to the current line. Empty-state connection and the new settings menu remain reachable.
- Existing clock, seek, offset, overlapping vocals, word modes, safe text insertion, search, artwork updates, 2,000-line documents and sound-reactive background checks pass. Reduced-motion and unavailable-WebGL fallbacks pass.
- Screenshots reviewed internally for phone and landscape tablet layouts. Test screenshot fixtures use original sample text and generated geometric cover art. Screenshots are not distributed.
- API 36 Java compile/D8/resources/package build passes. io.betterlyrics.mobile versionCode 5 / versionName 0.2.1. APK v2/v3 signatures verified with unchanged certificate SHA-256 5e017fe5163897e92ca83aa9a8b921ade3ccf065b2a6d53071c4369da74b8010.
- Scope is frontend presentation plus release metadata/User-Agent. The 176 Java/repository checks below are the prior 0.2.0 record and were not rerun for the cosmetic update. No Android hardware or emulator verification was performed.

## Previous 0.2.0 verification

# Validation record — 0.2.0

- Pure Java assertions: AudioSignalTests 29, ParserTests 79, MathTests 28, TextPolicyTests 19. Production repository offline regression: 21. Total: 176.
- Synthetic PCM verifies 86 Hz / 1 kHz / 8.5 kHz frequency separation, loudness response, DC/silence suppression, real transients, no metronome on a steady bass tone, pause reset, signed FFT + unsigned waveform input, bounded spectrum and partial reads.
- Actual bundled WebGL + DOM in Chrome headless: 25 functional groups across 360×740, 412×915 and 812×375, plus reduced-motion and blocked-WebGL contexts. Shader links; supplied audio features drive ripple/particle/energy states; stale audio decays; pause clears energy; static mode stops redraws; preview and both explicit native audio connection actions work. No browser JavaScript errors.
- Screenshot review performed internally; no screenshots are included as user deliverables. Browser inputs are simulated, not actual Android audio.
- API 36 Java/D8/APK package/signing checks. Package io.betterlyrics.mobile / versionCode 4 / versionName 0.2.0, same development signing certificate.

## Not validated on hardware

No Android device or emulator is attached. Visualizer session-0 availability, microphone-permission UI, MediaProjection consent/FGS lifecycle, actual Spotify/YouTube Music/Apple Music capture permissions, latency, battery/GPU cost and phone performance remain device tests. Do not treat JVM signal tests or mock browser input as proof of capturing a protected music app.

## Previous 0.1.2 verification

# Validation record — 0.1.2

- ParserTests: 79; MathTests: 28; TextPolicyTests: 19; production repository offline regression: 21. Total: 147 checks.
- New cases cover Chinese credit removal without time shifts, Chinese-language auxiliary tracks, Korean/English preferences, Chinese-first multilingual LRC, Japanese/Korean script distinctions, untouched original profanity, actual unmasked-source selection, YRC-to-LRC/plain fallback, clean-edition rejection, and masked-cache retries.
- Bundled app.js syntax check passes. The prior browser suite is recorded below for 0.1.1; a real Android device and a new browser runtime were not used for 0.1.2.
- API 36 Java/D8/APK packaging and v2/v3 signature verification pass. Package/version: io.betterlyrics.mobile / 3 / 0.1.2. Same development signing key as the preceding APK.
- No claim is made that censored upstream lyrics can always be recovered. Missing source words are not guessed. If only unmasked plain text is available, synchronized highlighting is unavailable for that selected document.

## Previous 0.1.1 verification

# Validation record — 0.1.1

## Executed

- Java 8 API-compatible parser/math compilation: ParserTests 79 assertions; MathTests 28 assertions.
- Offline production LyricsRepository regression checks: 11 passed. Wrong title rejection, synced-over-plain ranking, Korean artist/title aliases, cross-provider ranking, fast-mode fallback, romanized-YRC fallback to Korean LRC, impossible timestamp fallback, obsolete online cache invalidation, manual selection and local import retention.
- Actual HTTPS provider lookup with production repository code: 밤편지 / 아이유 → NetEase, 28 parsed rows; 봄날 (Spring Day) / 방탄소년단 (BTS) → LRCLIB, 61 rows; Ditto / NewJeans → LRCLIB, 48 rows. All selected documents had timed lines and Hangul. These checks do not compare the supplied times against recorded audio. LRCLIB also intermittently returned HTTP 503/timeouts; available-provider fallback was exercised.
- Existing live repository check: LRCLIB 20 candidates / 78 rows; NetEase 20 candidates / 115 real word-timed rows. Both cache round trips passed. No lyric text is printed or bundled with the tests.
- Actual bundled browser renderer with a mocked native bridge: 16 functional check groups, no JavaScript errors at 360×740, 412×915, 812×375. Same-track late artwork, clearing artwork on track changes, sampled clock delivery delay at 1×/2×, paused packets, and artwork/settings-only updates preserving the clock are covered.
- New native Java sources compile against API 36; D8, packaging, alignment and APK v2/v3 signature checks pass. Package io.betterlyrics.mobile, versionCode 2 / versionName 0.1.1. Signing certificate is the same development certificate as 0.1.0.

## Limits

- No real Android device/emulator is attached. Real notification-access binding, Spotify/YouTube Music metadata callbacks, content-URI grants, notification icon decoding, image URL reachability, overlays and actual sound-to-lyric timing must be checked on the user's phone.
- A source may have incorrect timestamps or no matching lyric document. Identity checks and timing preference reduce wrong selection; they cannot reconstruct missing or inaccurate source lyrics.
- Browser tests mock native media state. JVM HTTPS checks use JDK networking, not Android's runtime/trust store.
- Play Protect classification/installation approval is not established by the build or signing checks.
