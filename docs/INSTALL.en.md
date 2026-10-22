# Installation — English

[한국어](INSTALL.ko.md) · [中文](INSTALL.zh-CN.md) · [Project home](../README.en.md)

## 1. Install the APK

Requires **Android 8.0+**. Play music in a separate app, such as Spotify, YouTube Music, Apple Music or Samsung Music. Detection depends on that app exposing an Android media session. BetterLyrics' interface is currently Korean; the labels below match the actual interface.

1. **[Download the 0.3.0 APK](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.3.0.apk)**. On GitHub's file page, use **Download raw file**. **Code → Download ZIP** downloads source, not an installer.
2. Open `BetterLyrics-Mobile-0.3.0.apk`. If Android asks about the installation source, review **Settings → Allow from this source** for the browser/file app that opened it, then return to the installer. Labels vary by device.
3. Open BetterLyrics Mobile. You can revoke the installation-source permission afterward.

The APK is **103,221 bytes**. See its [SHA-256 checksum](../downloads/SHA256SUMS) and [signing information](../downloads/README.md). Matching these identifies the distributed file; it is not a security approval.

## 2. Connect music detection

1. Tap the top-right **··· → 음악 감지** (Music detection).
2. Select BetterLyrics in Android's notification-access screen, review the permission and allow it.
3. Start playback in your music app, then return. Use the bottom-right list button, **재생 앱 선택** (Select player), if several players are available.

If notification access is restricted **after installation**, and you trust the app and permission, check whether Android offers **Settings → Apps → BetterLyrics Mobile → More → Allow restricted settings**. Availability varies by Android version and device. This is separate from a Play Protect block during installation.

## 3. Optional controls

| Feature | Control |
| --- | --- |
| Floating lyrics | **··· → 플로팅 가사**; allow display over other apps when requested |
| Sound-reactive background | Center waveform button, or **··· → 사운드와 배경 → 재생 소리에 연결** |
| Alternative audio connection | **다른 방식으로 소리 연결** if the default fails; Android 10+ and system consent required |
| Lyrics/album view | Bottom-left quotation button |
| Per-song timing | **··· → 이 곡의 가사 시간 보정**; `+` displays lyrics earlier, `−` later |

Android may show an audio permission, but this implementation does not open microphone input. If the music app restricts playback capture, the default gradient remains available. Audio connection is optional for lyric detection.

## 4. Updates and troubleshooting

- **Updating:** An APK with the same package and signing certificate can update the existing app. A fresh source build generates your own key, different from the downloadable APK. A signature mismatch may require uninstalling the existing app, which deletes settings and imported lyrics. This version has no external backup feature.
- **Download fails:** Open the APK link in a regular browser or use **Download raw file** on GitHub. Do not rename an HTML page or source ZIP to `.apk`.
- **Play Protect blocks installation:** Stop and inspect the warning. This experimental build does not claim Google security approval. Disabling Play Protect is not part of these instructions. The developer should review Google's appeal process if the classification is incorrect.
- **Song/artwork missing:** Check notification access, play a song in the music app and select the player again. Artwork or playback data cannot appear if the player does not supply it.
- **Wrong lyrics or timing:** Use manual search in **···** to choose the same song/version, or import local lyrics. Use per-song correction for a constant offset. Missing or inaccurate source timestamps cannot always be repaired automatically.

Installation, permissions and music-app integration have not been validated on physical Android hardware. See the [validation scope](VALIDATION.md).

## Official Android help

- [Install apps from other sources](https://support.google.com/pixelphone/answer/7391672?hl=en)
- [Restricted settings](https://support.google.com/android/answer/12623953?hl=en)
- [Google Play Protect](https://support.google.com/googleplay/answer/2812853?hl=en)
