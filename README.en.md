# BetterLyrics Mobile

[한국어](README.md) · [English installation](docs/INSTALL.en.md) · [한국어 설치 안내](docs/INSTALL.ko.md) · [中文安装说明](docs/INSTALL.zh-CN.md)

An **unofficial experimental Android port** of BetterLyrics' music detection, lyric lookup and synchronization behavior. The original project is by [Zhe Fang / BetterLyrics contributors](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0), licensed under GPL-3.0. This project is independent of the official BetterLyrics project and Apple.

## Download and install

**[Download BetterLyrics-Mobile-0.3.0.apk](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.3.0.apk)** · Android 8.0+ · Universal APK · versionCode 7

1. Download and open the APK. If Android prompts, review **Allow from this source** for the browser/file app that opened it.
2. Open **··· → 음악 감지** (Music detection) and grant notification access to BetterLyrics.
3. Play a song in your music app, then return. The bottom-right list button opens **재생 앱 선택** (Select player).
4. Optionally enable floating lyrics or connect the sound-reactive background with the center waveform button.

See the [installation guide](docs/INSTALL.en.md) for permissions, Play Protect blocks, updates and synchronization troubleshooting. The app interface is currently Korean; translated documentation does not change the interface language.

## Features and scope

- Android MediaSession detection, artwork, transport/seek controls and Android media-volume control
- LRCLIB / NetEase lookup with candidate selection, title/artist/version/duration checks and local caching
- Line/word timing, progress-driven lyric flow, seek preview and per-song timing correction
- LRC, Enhanced LRC, ESLRC, TTML, decoded QRC/KRC/YRC and binary KRC import
- Album-color fluid gradients, optional sound response, portrait/landscape layouts and floating lyrics
- Hidden Chinese auxiliary translations/transliterations while preserving original lyrics; preference for available unmasked source text

Playback stays in another music app. Detection, artwork and transport depend on the metadata and controls that app exposes. Lyrics without word timing use line highlighting by default; missing words are not invented. A music app's capture restrictions can prevent sound-reactive effects. Windows plugins, QQ/Kugou/Apple Music online lyric sources and Apple Music Sing vocal removal are outside this version's scope. Encrypted QRC import is unsupported.

## Permissions and data

Notification access is used to connect music sessions. This implementation uses matching music-notification information and does not store or send ordinary message bodies. Online lookup sends title, artist, album and duration to the chosen lyric provider. HTTPS artwork URLs are downloaded from their image hosts. No music-account login or API key is required.

Sound response is optional. Android may request audio permission, but this implementation does not open microphone input. It uses Visualizer or, with user consent on Android 10+, AudioPlaybackCapture. Audio features are computed locally, are not stored or transmitted, and analysis stops when the activity leaves the foreground.

## Clone, pull and push

```sh
git clone https://github.com/sdcom1214/BetterlycisFor-mobile.git
cd BetterlycisFor-mobile
git pull --ff-only origin main
```

Review changes, stage only the intended files, commit and push. Pushing requires repository write access and GitHub authentication.

```sh
git status
git add README.en.md
git commit -m "docs: update installation guide"
git push origin main
```

## Build

Install Python 3, **JDK 17**, **Android SDK Platform 36 and Build Tools 36**. The verified build path uses a local SDK without downloading Gradle dependencies:

```sh
python3 tools/build.py --sdk /absolute/path/to/android-sdk
```

Output: `dist/BetterLyrics-Mobile-0.3.0.apk`. The first build generates `.signing/development.p12`. The public repository contains no private signing key, so your fresh build will have a different certificate from the downloadable APK. Updating an installed app requires the same package and signing key. Back up your own key privately; never commit it or use the development key for production distribution.

You can also open the Gradle project in Android Studio. The Python script above is the verified build path; a Gradle Wrapper is not included.

## Validation and references

```sh
python3 tools/test.py
python3 tools/network-test.py --sdk /absolute/path/to/android-sdk --json-jar /path/to/json-20250517.jar --offline
```

Optional browser checks require Playwright and Chromium. Set `BETTERLYRICS_CHROME` to use a specific Chrome executable.

```sh
node tools/ui-smoke.cjs
node tools/flow-smoke.cjs
node tools/player-smoke.cjs
```

Public repository preparation reran **176 offline Java/repository assertions** and rechecked the existing APK's v2/v3 signatures. The earlier 0.3.0 record includes 29 UI groups, 9 flow groups and 7 player groups with a mock native bridge, plus APK compilation. Browser suites were not rerun for this documentation/packaging change; application code is unchanged. **Installation, permissions and music-app integration have not been validated on physical Android hardware.**

- [Validation record](docs/VALIDATION.md) · [0.3.0 changes](docs/CHANGES-0.3.0.md)
- [Original source/package analysis](docs/ORIGINAL_ANALYSIS.md) · [UI references and differences](docs/APPLE-UI-REFERENCE.md)
- [GPL-3.0 license](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [APK checksum](downloads/SHA256SUMS)

No Apple fonts, images or code are bundled. The app uses Android system fonts and Bootstrap Icons under the MIT license.
