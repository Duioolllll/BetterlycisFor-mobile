# BetterLyrics Mobile installation — English

[한국어](INSTALL_KO.md) · [English](INSTALL_EN.md) · [简体中文](INSTALL_ZH.md) · [Home](../README.en.md)

**0.4.1 · Android8.0+(API26+) · package `io.betterlyrics.mobile` · versionCode9**
The app interface is Korean; translated installation guides do not add English/Chinese UI. This is an independent unofficial project. It targets Android16, but publication of0.4.1 requires Android16 emulator install/launch/update checks. Physical phones and music playback require separate tests. See [validation](VALIDATION.md).

## A. Download and verify

1. Open the [GitHub repository](https://github.com/sdcom1214/BetterlycisFor-mobile).
2. Choose **Releases**.
3. Open **Latest Release**, or use [this link](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/latest).
4. Expand **Assets** and download the APK. `Source code.zip/tar.gz` is not an installer.
5. This release's filename is `BetterLyrics-Mobile-0.4.1.apk`. For later versions, follow that release's version and filename.
6. If possible, compare the downloaded file's SHA-256 with the accompanying `SHA256SUMS`. Do not install a mismatch; download again.

[0.4.1 APK](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/download/v0.4.1/BetterLyrics-Mobile-0.4.1.apk) · [checksums](../downloads/SHA256SUMS). If Releases is temporarily unavailable, use the [repository APK](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.4.1.apk). Do not rename HTML pages or source ZIPs to APK.

On a computer:

```powershell
Get-FileHash .\BetterLyrics-Mobile-0.4.1.apk -Algorithm SHA256
```

Linux: `sha256sum BetterLyrics-Mobile-0.4.1.apk`; macOS: `shasum -a 256 BetterLyrics-Mobile-0.4.1.apk`. Matching hashes/signatures identify a file; they do not establish Google security approval.

## B. Install on Android

1. Open the downloaded APK.
2. Only if Android asks, enable **Allow from this source** for the browser/file app opening that APK, then return.
3. Check the **BetterLyrics Mobile** name and displayed permissions on the installer screen.
4. Complete installation. Special access, including notification access and audio, may be requested separately when first used.
5. You may disable that source's installation permission when it is no longer needed.

If Google Play Protect warns, read the exact message and reason. If it identifies a harmful app or says it is blocked to protect your device, stop installation and report the warning and version to this repository. This project does not claim Google review approval. Do not forcibly disable protection or automatically ignore warnings.

## C. First launch and use

1. Open the app. Tap top-right **··· → 음악 감지** (music detection), read Android's notification-access explanation, and allow it. This is needed to receive other music apps' media sessions and track information.
2. Start a song in Spotify, YouTube Music, Apple Music, Samsung Music, or another music app. BetterLyrics is not a music streaming service and does not request your music-account login.
3. Return for automatic lyric search. If multiple sessions exist, use the bottom list button **재생 앱 선택** (select player). The player must expose a standard Android MediaSession. Listing these apps does not mean each has been tested on hardware.
4. Compare the highlighted line with the song. For a different song version, use **가사 직접 찾기** (manual search), **다시 검색** (retry), or **가사 파일 불러오기** (import lyrics) in **···**. Plain text without timing cannot synchronize precisely.
5. For a constant offset use **이 곡의 가사 시간 보정** (this song's timing adjustment). `+` displays lyrics earlier; `−` later. Check again after seeking/pausing.
6. Open **··· → 화면 꾸미기** (appearance): original/focus/album presets, layout, alignment, font size/weight, spacing, blur, brightness, cover, color and controls. Changes apply immediately and are saved. Resetting the preset preserves song timing offsets. Floating lyrics use a separate style.
7. In **사운드와 배경** (sound/background), choose reactive/fluid/static and intensity. For reactive input select **재생 소리에 연결** (connect playback sound) and allow audio permission. If the default method fails, Android10+ offers **다른 방식으로 소리 연결** (alternative connection) with a system sharing-consent prompt.

Audio permission is used for playback analysis; this implementation does not open microphone input or save/upload recordings. If the music app blocks capture, a base gradient remains. Sound connection is optional for lyrics, and analysis stops when leaving the app screen. **Display over other apps** is needed only for floating lyrics. File import reads only the selected document.

If notification access is locked by “restricted settings,” that is separate from an installation block. First assess whether you trust the installed app and permission, then follow Android's restricted-settings explanation in App info. Do not bypass organization/parental restrictions.

## D. Update

Download the latest APK from GitHub Releases and install over the existing app. **Package and signing identity must match, and versionCode must not be lower.** This release was checked against the previous distribution's certificate. Self-built debug APKs or new signing keys can differ.

If an update fails, check the hash, version, package and signature. For a signature mismatch, obtain an APK signed with the original distributor's key. Uninstall/reinstall is a last resort: settings, per-song offsets, imported lyric copies and cache can be lost. There is no in-app external backup/export feature. Keep original lyric files and needed settings separately.

## E. Troubleshooting

| Problem | Checks and actions |
| --- | --- |
| 0.4.0 installation fails | Confirmed resource packaging defect: download0.4.1 again; renaming the old file does not fix it |
| APK installation fails | Check Android8+, free space, complete download/hash and correct APK. For updates also check signature/version |
| Installation blocked | Check source-specific permission for the app opening the file. Ask the administrator about organization/parental policies |
| Play Protect warning | Read it; stop for a harmful-app verdict. Do not force protection off. Report exact wording/version |
| Crash or blank screen at launch | Update Android System WebView/Chrome and reopen. If persistent, report device/Android/app versions and sanitized ADB logs. Crash-free hardware operation is unverified |
| Player not detected/disconnected | Check notification access, active playback and select player→refresh. Returning to the app requests reconnection. Missing MediaSession support and battery policies remain limitations |
| No lyrics/long search | Check network/provider, try another provider/manual search/local file. Timeouts exist, but multiple candidates may take tens of seconds |
| Korean lyrics out of sync | Select matching title/artist/album version/duration. Adjust a constant offset per song. Irregular errors may be source timestamps: use another lyric result/local file. Not every song can be corrected automatically |
| Missing album art | Play and reselect the music app; check network. Metadata/URI/matching-session notification art is attempted, but the source app must provide an image and access |
| Missing background effects | Check mode/intensity/brightness/sound connection. Static mode, reduced motion, pause, silence, blocked capture or missing WebGL reduce effects or use a gradient |
| Android permission error | Check notification access, overlay, audio and sharing consent separately. Re-enable only the optional feature needed; granting everything is unnecessary |
| Android16 issue | Only target36 build/code review is complete. Update WebView and report permission, system-bar, rotation and sharing-stop behavior. Locking/leaving the app may require new sound-sharing consent |

[Official installation help](https://support.google.com/pixelphone/answer/7391672) · [Restricted settings](https://support.google.com/android/answer/12623953) · [Play Protect](https://support.google.com/googleplay/answer/2812853)
