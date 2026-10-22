# BetterLyrics Mobile

An independent, unofficial Android lyric companion based on analysis of BetterLyrics. It is not an official product of BetterLyrics, Apple or any music service.

**0.4.1 · Android8.0+(API26) · target Android16(API36) · versionCode9**

**[Download APK](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/download/v0.4.1/BetterLyrics-Mobile-0.4.1.apk)** · [Latest Release / Assets](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/latest) · [Fallback download](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.4.1.apk) · [SHA-256](downloads/SHA256SUMS)

[한국어 README](README.md) · Installation: [한국어](docs/INSTALL_KO.md) / [English](docs/INSTALL_EN.md) / [简体中文](docs/INSTALL_ZH.md)

![Landscape lyric UI](docs/images/lyrics-wide.webp)

Actual bundled web UI rendered with synthetic metadata and original sample text; a browser capture, not an Android hardware capture. Runtime album art comes from your music app.

**Installation fix:**0.4.0 has a confirmed resource-table packaging defect. Use0.4.1 or later. [Changelog](CHANGELOG.md)

## Features

MediaSession detection, album art, transport/seek/volume; LRCLIB and unofficial NetEase lyrics; Korean aliases/version matching; local lyric import; real word/line timing; continuous progress, scrub preview and per-song offset. Original/focus/album presets and persistent appearance controls cover layout, type, spacing, blur, color, brightness, cover and auto-hiding controls. Album-color gradients, optional sound-reactive backgrounds, reduced-motion fallback and floating lyrics remain available.

The interface is currently Korean. See the translated installation guides for its control labels. Allow notification access only after reading the system prompt, play music in a compatible app, then return. Audio and overlay permissions are optional. Read Play Protect warnings and stop if the app is identified as harmful; do not disable protection to force installation.

## Verification and limits

Linux debug/release builds, APK v2/v3 signature and previous signing-certificate match, two identical same-environment builds, 155 Java assertions and5 packaging cases were checked for this fix; previous UI regressions are retained in validation records. Three Korean songs returned timed Hangul lyrics from live providers; this does not establish listening synchronization on a phone. Intermittent LRCLIB HTTP503 was observed.

Publication of0.4.1 requires Android16 emulator install/launch/update checks; see the attached INSTALL_VALIDATION.json. Physical phones, permissions, real media/capture integration, reconnection, performance and battery use remain unverified. NetEase is unofficial and its usage/distribution terms are not cleared. Missing source words are not invented. QQ/Kugou/Apple online lyrics, encrypted QRC, Windows plug-ins, Wallpaper Engine and Sing vocal removal are excluded. Existing development-certificate lineage is retained for updates; this is not Google security certification.

## Development and references

Java8 source/target, JDK17, SDK36 / Build Tools36.0.0, local HTML/CSS/JS WebView + WebGL, Python3 builder. No external Android runtime framework. AGP8.10.1 configuration exists, but Gradle execution was not verified.

```sh
git clone https://github.com/sdcom1214/BetterlycisFor-mobile.git
cd BetterlycisFor-mobile
python3 tools/build.py --variant debug --sdk /path/to/Android/Sdk
python3 tools/test.py
```

[Build/sign/ADB/reproducibility](docs/BUILD.md) · [Development](docs/DEVELOPMENT.md) · [API](docs/API_REFERENCE.md) · [Reverse engineering](docs/REVERSE_ENGINEERING.md) · [Validation](docs/VALIDATION.md) · [Release notes](docs/RELEASE-0.4.1.md) · [Security/licenses](docs/SECURITY_AND_LICENSES.md).

Original: [Zhe Fang / BetterLyrics contributors](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0). Android changes: BetterLyrics Mobile contributors, 2026. [GPL-3.0](LICENSE), no warranty. Corresponding source/build scripts are available at the release tag; private signing keys are excluded. Bootstrap Icons1.13.1: MIT. [Third-party notices](THIRD_PARTY_NOTICES.md). Lyrics, artwork and trademarks retain their separate rights; software licensing does not grant content redistribution rights.
