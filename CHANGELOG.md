# Changelog

## 0.4.2 — 2026-10-23

### Fixed

- 오디오 캡처 서비스 종료 처리: 서비스가 종료될 때 이미 안내한 구체적인 실패 사유(공유 종료, 앱별 캡처 실패, 연결 끊김)를 일반적인 "연결이 종료됐어요" 문구로 덮어쓰지 않는다. 녹음 장치 해제도 읽기 스레드와 경합하지 않도록 정리했다.
- WebView 브리지 오류 안내: 내부 예외 원문을 토스트에 그대로 노출하지 않고 일반 안내 문구로 바꿨다. 진단 정보는 로그에만 남긴다.
- 가사 요청 User-Agent의 저장소 주소를 계정명 변경(GitHub sdcom1214 → Duioolllll)에 맞게 고치고 버전을 0.4.2로 올렸다. 패키지명과 원래 인증서를 유지하면서 versionCode를 9→10으로 올렸다.

### Validation and prevention

- 오프라인 회귀 검사(tools/test.py)가 기본 검사 묶음을 명시적으로 실행하고, 네트워크 검사는 옵션을 켰을 때만 실행한다. Android SDK(android-36 android.jar)가 없으면 즉시 실패한다.
- 릴리즈 워크플로가 test.py에 SDK 경로를 명시적으로 전달한다(--sdk). 로컬 실행 시 ANDROID_HOME도 폴백으로 사용한다.
- 게시 조건은 0.4.1과 동일하다: Android 16 에뮬레이터에서 신규 설치·시작·기존 버전 덮어쓰기 업데이트를 통과해야 한다. 결과는 Release의 `INSTALL_VALIDATION.json`에 첨부된다.

### English

Capture-service teardown no longer overwrites a specific failure reason with a generic disconnect message, and recorder release no longer races the reader thread. WebView bridge errors show a generic toast instead of raw exception text (diagnostics go to logcat). The lyrics User-Agent uses the renamed GitHub account URL. VersionCode 10 keeps the same package and original certificate. The offline regression runner executes an explicit default check set with network checks opt-in and fails fast when the Android SDK is missing; the release workflow passes the SDK path explicitly.

## 0.4.1 — 2026-10-23

### Fixed

- Android 설치를 막던 APK 리소스 패키징을 수정했다. 0.4.0의 결정적 ZIP 재작성 과정이 `resources.arsc`까지 DEFLATE로 압축했다. Android 11 이상 대상 앱은 이 파일을 비압축·4바이트 정렬로 저장해야 한다.
- 0.4.0의 실제 값은 압축 방식 8(DEFLATE), 데이터 offset 2407이었다. 0.4.1은 방식 0(STORED), offset 2412로 변경했다.
- AAPT2가 생성한 항목의 압축 방식을 보존하고 `resources.arsc`는 명시적으로 STORED로 기록한다. 이후 zipalign을 적용하고 서명 전후의 최종 데이터 offset을 확인한다.
- 패키지명과 원래 인증서를 유지하면서 versionCode를 8→9, versionName을 0.4.0→0.4.1로 올렸다. UI/UX·가사·음악 연동 기능은 변경하지 않았다.

### Validation and prevention

- 실제 0.4.0 APK를 거부하고 0.3.0/수정 APK를 통과시키는 패키징 회귀 검사 및 잘못된 정렬/누락 리소스 검사를 추가했다.
- 서명/체크섬/zipalign 성공만으로 설치 가능하다고 판단하던 검증의 빈틈을 보완했다. release 검증도 압축 방식과 로컬 ZIP 헤더의 실제 데이터 정렬을 검사한다.
- 로컬: Java 파서/매칭/정책/오디오 신호 155개 검사, 패키징 5개 검사, APK v2/v3 서명, 기존 인증서 일치, 두 번의 동일 빌드 결과를 확인했다.
- 배포 gate: Android 16(API36) 전용 AVD에서 0.4.0 설치 실패 재현, 0.4.1 신규 설치, MainActivity 시작, 0.3.0→0.4.1 덮어쓰기 업데이트를 실행해야 Release를 게시한다. 해당 실행 결과는 Release의 `INSTALL_VALIDATION.json`과 Actions 로그로 제공한다.
- 실제 사용자 기기의 설치 결과, Play Protect 판단, 다른 서명으로 빌드한 기존 앱과의 업데이트, 실제 음악/권한/오디오 연동은 별도 확인이 필요하다. 서명이 다른 앱을 해결하려고 설정/데이터가 지워지는 삭제를 자동 안내하지 않는다.

### English

Fix an APK packaging regression that compressed `resources.arsc` in 0.4.0. Android R+ targets require an uncompressed, 4-byte-aligned resource table. Preserve AAPT2 compression choices, store the table explicitly, and validate its final local-header data offset before/after signing. VersionCode9 retains the original package and signing identity. Add real-release/negative packaging checks and an Android16 emulator installation/update gate. See `INSTALL_VALIDATION.json` for that run; physical-device and music integration remain outside this check.

## 0.4.0 — 2026-10-23

Added appearance presets/customization and multilingual release documentation. **This version has a confirmed Android installation packaging defect; use 0.4.1 or later.** Details: [0.4.0 notes](docs/RELEASE-0.4.0.md).

## 0.3.0

See [0.3.0 changes](docs/CHANGES-0.3.0.md).
