# BetterLyrics Mobile 0.4.1 — APK 설치 오류 수정

Release date: **2026-10-23** · Android8.0+(min26), target Android16(API36).

**[APK 다운로드 / Download APK](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/download/v0.4.1/BetterLyrics-Mobile-0.4.1.apk)** · [SHA256SUMS](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/download/v0.4.1/SHA256SUMS)

`BetterLyrics-Mobile-0.4.1.apk` · versionCode9 · `io.betterlyrics.mobile` · 기존 서명 유지.

SHA-256: `1af5849e88074fa84688f31c2f2926df950bb55145d3eaf2f95d33bac14ff6fd`

## 한국어

0.4.0에서 앱 설치를 막던 리소스 패키징 오류를 수정했습니다. APK를 다시 묶는 과정에서 `resources.arsc`가 압축됐으며, Android11 이상 대상 앱에 필요한 비압축·4바이트 정렬 조건을 충족하지 못했습니다. 0.4.1은 해당 파일을 비압축으로 저장하고 정렬 후 서명 전후를 검사합니다.

- 패키지명과 원래 서명을 유지하고 버전 코드를 올렸습니다. 설치된 0.3.0 등 동일 서명 앱 위에 업데이트할 수 있도록 구성했습니다.
- UI 커스터마이징·가사·배경·음악 연동 기능은 유지했습니다.
- 실제 0.4.0 APK를 실패 사례로 사용하는 회귀 검사와 release 차단 검사를 추가했습니다.
- 로컬에서 Java155개·패키징5개 검사, v2/v3 서명·기존 인증서, 같은 환경 두 번의 빌드 일치를 확인했습니다.
- 게시 조건: Android16 가상 기기에서 기존 실패 재현, 새 APK 설치·시작, 0.3.0에서 덮어쓰기 업데이트를 통과해야 이 Release가 게시됩니다. **첨부한 `INSTALL_VALIDATION.json`이 실행 결과입니다.**

0.4.0을 받았다면 0.4.1 APK를 새로 다운로드하세요. 서명이 다른 직접 빌드 앱, 저장 공간 부족, 손상된 다운로드, Play Protect 차단은 별개 원인입니다. 위험 경고를 강제로 무시하거나 보안 기능을 끄지 마세요. 앱 삭제는 설정/가사 데이터를 지울 수 있으므로 먼저 정확한 오류를 확인하세요. 실제 휴대폰과 모든 음악/권한/오디오 기능을 검증했다는 뜻은 아닙니다.

## English

Fixes the 0.4.0 installation blocker: deterministic ZIP repackaging compressed `resources.arsc`, violating Android R+ requirements. The table is now explicitly stored uncompressed, zip-aligned, and validated before and after signing. VersionCode9 retains the same package and original certificate. UI, lyrics and playback features are unchanged.

Local checks:155 Java assertions,5 packaging cases, v2/v3 signing, prior certificate match and two identical same-environment builds. Publication requires an Android16 emulator to reproduce the old rejection, install/launch the fixed APK and update0.3.0 in place. The attached `INSTALL_VALIDATION.json` records the actual run. This does not establish physical-phone compatibility, Play Protect approval or full media/permission/capture functionality.

Download0.4.1 again rather than retrying0.4.0. A different signing key, damaged download, insufficient storage or security block requires separate diagnosis. Do not disable protection to force installation; uninstalling can lose app data.

[한국어 설치](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.1/docs/INSTALL_KO.md) · [English](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.1/docs/INSTALL_EN.md) · [简体中文](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.1/docs/INSTALL_ZH.md)

[Changelog](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.1/CHANGELOG.md) · [Build](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.1/docs/BUILD.md) · [Validation](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.1/docs/VALIDATION.md)

Technical reference: [Android11 compressed resources restriction](https://developer.android.com/about/versions/11/behavior-changes-11#compressed-resource-file). Independent unofficial project; GPL-3.0; original BetterLyrics attribution retained.
