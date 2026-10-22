# Changelog

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
