# Download verification — 0.3.0

[Download APK](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.3.0.apk) · [한국어](../docs/INSTALL.ko.md) · [English](../docs/INSTALL.en.md) · [中文](../docs/INSTALL.zh-CN.md)

| Field | Value |
| --- | --- |
| File | BetterLyrics-Mobile-0.3.0.apk |
| Size | 103,221 bytes |
| Package | io.betterlyrics.mobile |
| Version | 0.3.0 / versionCode 7 |
| Minimum Android | 8.0 / API 26 |
| Target API | 36 |
| ABI | Universal; no native `.so` libraries |
| Signing | Private development certificate; APK v2/v3 signatures verified |
| APK SHA-256 | `951c239e45fc4735b4112403f96370cec22506b2003d1daa38ce5c429b1e60ca` |
| Certificate SHA-256 | `5e017fe5163897e92ca83aa9a8b921ade3ccf065b2a6d53071c4369da74b8010` |

From this directory, verify the downloaded file on Linux:

```sh
sha256sum -c SHA256SUMS
```

On macOS:

```sh
shasum -a 256 -c SHA256SUMS
```

On Windows PowerShell:

```powershell
Get-FileHash .\BetterLyrics-Mobile-0.3.0.apk -Algorithm SHA256
```

These checks identify this test artifact. They do not establish Google Play Protect approval or actual-device compatibility. The private signing key is excluded from the repository. Fresh source builds create their own local key and cannot update this APK unless signed with the same certificate.
