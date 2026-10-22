# BetterLyrics Mobile 0.4.0

릴리스 날짜 / Release date: **2026-10-22**

**[APK 다운로드 / Download](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/download/v0.4.0/BetterLyrics-Mobile-0.4.0.apk)**

`BetterLyrics-Mobile-0.4.0.apk` · 111,554 bytes · versionCode8 · `io.betterlyrics.mobile`

Android8.0+(API26), target/compile Android16(API36), universal APK(no native .so).

SHA-256:

```text
bbd1926670d52f52f5a8a30adbf07967f1cc89b49e68e57004c45ca63f99f0de
```

## 한국어

원본 BetterLyrics 화면을 참고한 모바일 UI와 커스터마이징, 최종 빌드·설치·기술 문서를 제공합니다. 독립적인 비공식 프로젝트입니다.

- 새 기능: 원본 스타일/가사 집중/앨범 중심 프리셋, 배치·정렬·글자·여백·흐림·밝기·커버·색상·재생 컨트롤 설정, 연속/줄 고정 이동, 설정 즉시 적용·저장.
- 수정: 앨범 보기의 컨트롤 겹침, 수동 가사 읽기의 흐림, 모달 배경 키보드 접근을 수정했습니다. 알림 리스너 종료 후 권한이 남으면 재연결을 요청하고 HTTP 본문 읽기에 중단·시간 제한을 추가했습니다. 실제 기기에서 해당 서비스 문제의 해결 여부는 미검증입니다.
- 유지: 진행도/단어 강조, 한국어 후보·시간 정보 처리, 앨범 아트 fallback, MediaSession 제어, 실제 소리 반응/기본 그라데이션, 플로팅 가사.
- 빌드: 명시적 debug/release, 필수 외부 release 키, 기존 인증서 유지, 고정 ZIP 날짜/정렬. 같은 환경에서 두 번 빌드한 APK 전체 해시가 같습니다.
- 검증: JVM190개, 브라우저50개 검사 묶음, release v2/v3 서명·해시·기존 인증서·패키지 검증. 한국어3곡의 실제 응답에서 한글/시간 구조 확인; LRCLIB 일시적503 관찰.
- 문서: 한국어/영어/중국어 설치, Windows/Linux/macOS 준비, 빌드·서명·ADB·재현, API분류, REA 기록·Android 재구현, 보안/라이선스 범위.

**알려진 문제:** 실제 Android 기기/에뮬레이터 테스트는 하지 않았습니다. Android16 설치·권한·재연결·실제 청취 싱크·오디오 캡처·성능은 확인이 필요합니다. NetEase 비공식 API 이용/배포 조건은 미확인입니다. 앱 UI는 한국어이며 음악 앱/공급자에 따라 커버·가사·캡처가 제한됩니다. 기존 개발용 인증서 계보이며 Play Protect 승인을 주장하지 않습니다. 위험 경고를 강제로 무시하지 마세요. 같은 패키지·서명의 이전 APK만 덮어쓰기 업데이트할 수 있습니다.

## English

This independent unofficial release adds an upstream-inspired mobile UI, appearance customization and complete installation/developer documentation.

- New: original/focus/album presets; layout, alignment, typography, spacing, blur, brightness, cover, color and control visibility; continuous/line-centered flow; immediate saved preferences.
- Fixes: album/footer overlap, manual-reading blur, keyboard access behind modal dialogs. Notification-listener disconnect now requests rebinding when permission remains; HTTP body reads gain interruption/time checks. Native lifecycle fixes still require device confirmation.
- Retained: progress/word highlighting, Korean matching/timing fallback, artwork fallback, MediaSession controls, sound-reactive/gradient backgrounds and floating lyrics.
- Build: explicit debug/release, existing external release key required, prior signing identity preserved, deterministic ZIP packaging. Two same-environment builds produced identical APK bytes.
- Verified:190 offline JVM assertions,50 browser check groups, v2/v3 signature, hash, previous certificate and package metadata. Three live Korean-song queries returned timed Hangul lyrics; transient LRCLIB503 responses were observed.
- Documentation: three-language installation guides, OS setup, builds/signing/ADB/reproducibility, API classification, REA evidence and Android implementation, security/license limitations.

**Known issues:** no Android hardware/emulator execution was performed. Android16 installation, permissions, reconnection, listening synchronization, real capture and performance remain unverified. Unofficial NetEase usage/distribution terms are unresolved. The interface is Korean. Artwork/lyrics/capture depend on the source app/provider. The previous development-certificate lineage is retained; this is not Play Protect approval. Do not override harmful-app warnings. In-place updates require matching package and signature.

## Guides / 안내

- [한국어 설치](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/INSTALL_KO.md)
- [English installation](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/INSTALL_EN.md)
- [简体中文安装](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/INSTALL_ZH.md)
- [Build](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/BUILD.md) · [Validation](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/VALIDATION.md)
- [API](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/API_REFERENCE.md) · [Reverse engineering](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/REVERSE_ENGINEERING.md) · [Licenses](https://github.com/sdcom1214/BetterlycisFor-mobile/blob/v0.4.0/docs/SECURITY_AND_LICENSES.md)

Original BetterLyrics: Zhe Fang / contributors. Mobile adaptation: BetterLyrics Mobile contributors,2026. GPL-3.0, no warranty. Icons: Bootstrap Icons(MIT). The tag contains corresponding source/build scripts; private signing keys and third-party lyric/artwork content are excluded.
