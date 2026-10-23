# BetterLyrics Mobile 0.4.2 — 오디오 종료 처리와 브리지 오류 안내 수정

> 이 버전은 소스·메타데이터만 커밋됐고 GitHub Release/APK로 게시되지 않았다. 수정 사항은 [0.4.3](RELEASE-0.4.3.md)에 포함된다. 아래는 당시 준비한 릴리스 노트이며 설치 검증 통과 기록을 의미하지 않는다.

Release date: **2026-10-23** · Android8.0+(min26), target Android16(API36).

**[APK 다운로드 / Download APK](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.4.2/BetterLyrics-Mobile-0.4.2.apk)** · [SHA256SUMS](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.4.2/SHA256SUMS)

`BetterLyrics-Mobile-0.4.2.apk` · versionCode10 · `io.betterlyrics.mobile` · 기존 서명 유지.

SHA-256: `50e826bbfd9eada257289de795cb9fd2c57650a2a4cd4860d6e17f909b384096`

## 한국어

소리 공유(오디오 캡처)가 끝날 때 표시되는 안내와 WebView 브리지 오류 안내를 다듬은 유지보수 릴리즈입니다. 기능 추가는 없습니다.

- 캡처 서비스가 종료될 때 이미 안내한 구체적인 실패 사유를 일반 문구로 덮어쓰지 않습니다. 녹음 장치 해제도 읽기 스레드와 경합하지 않게 정리했습니다.
- 브리지 명령이 실패하면 내부 예외 원문 대신 일반 안내 문구를 표시합니다. 진단 정보는 로그에만 남습니다.
- 가사 요청 User-Agent의 저장소 주소를 계정명 변경에 맞게 고쳤습니다.
- 오프라인 회귀 검사는 기본 검사 묶음을 명시적으로 실행하고 네트워크 검사는 선택 사항으로 분리했습니다. SDK가 없으면 즉시 실패합니다.
- 패키지명과 원래 서명을 유지하고 버전 코드를 올렸습니다. 설치된 동일 서명 앱 위에 업데이트할 수 있습니다.
- 게시 조건: Android16 가상 기기에서 새 APK 설치·시작, 기존 버전에서 덮어쓰기 업데이트를 통과해야 이 Release가 게시됩니다. **첨부한 `INSTALL_VALIDATION.json`이 실행 결과입니다.**

서명이 다른 직접 빌드 앱, 저장 공간 부족, 손상된 다운로드, Play Protect 차단은 별개 원인입니다. 위험 경고를 강제로 무시하거나 보안 기능을 끄지 마세요. 앱 삭제는 설정/가사 데이터를 지울 수 있으므로 먼저 정확한 오류를 확인하세요. 실제 휴대폰과 모든 음악/권한/오디오 기능을 검증했다는 뜻은 아닙니다.

## English

Maintenance release for audio-capture teardown and WebView bridge error reporting. No new features.

The capture service no longer overwrites a specific failure reason (sharing stopped, per-app capture failure, disconnect) with a generic message when it is destroyed, and recorder release no longer races the reader thread. Bridge command failures now show a generic toast instead of raw exception text; diagnostics go to logcat only. The lyrics User-Agent points at the renamed GitHub account. The offline regression runner executes an explicit default check set, keeps network checks opt-in, and fails fast when the Android SDK is missing.

VersionCode10 retains the same package and original certificate, so it updates an installed same-signing build in place. Publication requires an Android16 emulator to install/launch the new APK and update the previous version in place. The attached `INSTALL_VALIDATION.json` records the actual run. This does not establish physical-phone compatibility, Play Protect approval or full media/permission/capture functionality.

A different signing key, damaged download, insufficient storage or security block requires separate diagnosis. Do not disable protection to force installation; uninstalling can lose app data.

[한국어 설치](https://github.com/Duioolllll/BetterlycisFor-mobile/blob/v0.4.2/docs/INSTALL_KO.md) · [English](https://github.com/Duioolllll/BetterlycisFor-mobile/blob/v0.4.2/docs/INSTALL_EN.md) · [简体中文](https://github.com/Duioolllll/BetterlycisFor-mobile/blob/v0.4.2/docs/INSTALL_ZH.md)

[Changelog](https://github.com/Duioolllll/BetterlycisFor-mobile/blob/v0.4.2/CHANGELOG.md) · [Build](https://github.com/Duioolllll/BetterlycisFor-mobile/blob/v0.4.2/docs/BUILD.md) · [Validation](https://github.com/Duioolllll/BetterlycisFor-mobile/blob/v0.4.2/docs/VALIDATION.md)

Independent unofficial project; GPL-3.0; original BetterLyrics attribution retained.
