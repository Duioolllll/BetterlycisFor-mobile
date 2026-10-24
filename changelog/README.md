# 릴리스와 변경 기록 · Release history

[프로젝트 홈](../README.md) · [English](../README.en.md) · [GitHub Releases](https://github.com/Duioolllll/BetterlycisFor-mobile/releases)

## 현재 배포 · v0.4.4

**2026-10-24 · Android 8.0+ · versionCode 12**

**[↓ APK 다운로드 / Download APK](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.4.4/BetterLyrics-Mobile-0.4.4.apk)** · [Release / 검증 첨부 파일](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/tag/v0.4.4) · [0.4.4 전체 노트](0.4.4.md)

서비스 I/O·오디오 처리 최적화, 캐시 저장 경합 방지, 오디오 종료·오류 안내 수정. Android 16 AVD 신규 설치·시작 및 0.3.0 / 0.4.1에서 업데이트·시작을 통과했습니다. 실제 휴대폰·음악 앱 연동은 별도 검증이 필요합니다.

설치: [한국어](../docs/INSTALL_KO.md) · [English](../docs/INSTALL_EN.md) · [简体中文](../docs/INSTALL_ZH.md)

## 전체 기록 · All records

| 버전 / 문서 | 배포 상태 / Status | 주요 내용 / Highlights |
| --- | --- | --- |
| [Unreleased](UNRELEASED.md) | 문서·배포 구성 변경 / Documentation only | 첫 화면과 변경 기록 정리. 새 APK 없음 / Landing page and history organization; no new APK |
| [0.4.4](0.4.4.md) | **최신 공개 APK / Current public APK** | FFT 계수 재사용 / Cached FFT plan |
| [0.4.3](0.4.3.md) | 이전 공개 APK / Previous APK | 서비스 I/O·오디오 처리 최적화 / Service I/O and audio processing |
| [0.4.2](0.4.2.md) | **소스만 기록, APK 미공개 / Source only** | 오디오 종료·브리지 안내 수정, 0.4.3에 포함 / Capture teardown and bridge errors, included in 0.4.3 |
| [0.4.1](0.4.1.md) | 이전 공개 APK / Previous public APK | Android 설치 패키징 수정 / Installation packaging fix |
| [0.4.0](0.4.0.md) | **설치 결함, prerelease / Known installation defect** | UI 커스터마이징·기술 문서 / Appearance customization and technical docs |
| [0.3.0](0.3.0.md) | 이전 APK·변경 기록 / Retained APK and notes | Apple Music 참고 플레이어 UI / Apple Music-inspired player layout |
| [0.2.2](0.2.2.md) | 변경 기록 / Historical notes | 진행도 기반 가사 흐름 / Continuous lyric flow |
| [0.2.1](0.2.1.md) | 변경 기록 / Historical notes | 가사 집중 UI / Lyric-focused UI |
| [0.2.0](0.2.0.md) | 변경 기록 / Historical notes | 소리 반응 배경 / Sound-reactive background |
| [0.1.2](0.1.2.md) | 변경 기록 / Historical notes | 보조 언어·크레딧·마스킹 정책 / Text and masking policy |
| [0.1.1](0.1.1.md) | 변경 기록 / Historical notes | 한국어 매칭·싱크·커버 / Korean matching, timing and artwork |
| [HISTORY](HISTORY.md) | 이전 누적 기록 / Previous cumulative log | 기존 루트 CHANGELOG 내용 보존 / Preserved former root changelog |

초기 기록은 저장소에 남은 변경 문서입니다. 모든 버전에 GitHub Release가 있었다는 뜻은 아닙니다. 각 문서의 검증 결과는 **해당 시점**의 범위이며 현재 APK의 검증으로 합산하지 않습니다. 게시 상태는 확인된 GitHub Releases와 저장소 APK를 기준으로 구분했습니다.

Early entries preserve repository change notes; they do not imply that every version had a GitHub Release. Validation belongs to the version that performed it. Publication status distinguishes confirmed releases, retained APKs and source-only records.

## 이후 릴리스 작성 방법 · Maintaining this folder

1. 배포 전 변경은 [`UNRELEASED.md`](UNRELEASED.md)에 작성합니다. / Record pending changes there.
2. APK 버전 확정 시 `changelog/<version>.md`에 한국어·영어 릴리스 노트와 실제 검증·미검증 항목을 작성합니다. / Create versioned Korean/English notes with actual results and limits.
3. 이 색인과 README, 다운로드 메타데이터를 맞춥니다. / Align the index, landing pages and download metadata.
4. 게시 워크플로는 `downloads/release.json`의 버전을 읽고 **`changelog/<version>.md`**를 Release 설명으로 사용합니다. APK·서명·Android 16 설치 검사 통과 후 게시합니다. / Publication uses that versioned note after the configured package/signature/emulator checks.

기존 태그와 게시된 APK는 보존합니다. 이번 문서 정리로 APK 버전·서명·체크섬을 바꾸지 않습니다. 이전 Release가 가리키는 태그 안의 `docs/RELEASE-*.md`는 해당 태그에 그대로 남아 있습니다.

Existing tags and published APKs are retained. This documentation update does not change the APK version, signature or checksum. Old tagged `docs/RELEASE-*.md` links still refer to the preserved tag contents.
