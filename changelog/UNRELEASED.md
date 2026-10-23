# Unreleased — 문서·배포 구성 / Documentation and release presentation

[← 전체 변경 기록 / All records](README.md)

## 2026-10-23 · 문서 정리 / Documentation update

이 변경은 저장소 문서와 릴리스 노트 경로 정리입니다. **배포 APK는 0.4.3 (11)로 유지합니다.**

- 한국어·영어 README를 다운로드 중심의 배포 첫 화면으로 구성했습니다. 버전·지원 Android·용량·스크린샷·설치·검증·기술 문서를 연결했습니다.
- 기존 `docs/CHANGES-*.md`, `docs/RELEASE-*.md`와 누적 Changelog를 `changelog/`로 모으고 버전별 색인을 추가했습니다. 루트 `CHANGELOG.md`는 기존 진입점으로 남겼습니다.
- 현재 APK, 이전 APK, 소스만 기록된 0.4.2, 설치 결함이 있는 0.4.0을 구분했습니다. 0.4.2의 미공개 APK·태그 링크와 출처를 확인할 수 없는 체크섬을 제거했습니다.
- 문서 링크와 게시 워크플로의 `--notes-file` 경로를 새 폴더에 맞췄습니다. 기존 태그·게시 APK·서명·체크섬은 보존합니다.

This update changes repository documentation and the release-note path. **The distributed APK remains 0.4.3 (11).**

- Rebuilt the Korean/English landing pages around download, version, Android support, screenshots, installation, validation and developer references.
- Collected all existing version notes and the cumulative changelog in `changelog/`, with a version index and a root compatibility entry point.
- Distinguished current/older APKs, source-only 0.4.2 and the defective 0.4.0 package. Removed nonexistent 0.4.2 asset/tag links and the unsubstantiated checksum.
- Updated current document links and the publisher's `--notes-file` path. Retained existing tags, APKs, signing identity and checksums.

## 확인한 항목 / Checks performed

- Markdown 37개를 GFM으로 변환해 로컬 링크 212개와 이미지 10개의 참조·대체 텍스트를 검사했습니다. 끊어진 로컬 링크는 없었습니다.
- 버전 기록 10개가 새 폴더에 있고, 0.4.2·0.4.3을 제외한 이전 기록 8개의 본문이 보존됐는지 비교했습니다.
- 릴리스 워크플로의 YAML과 모든 Bash 블록 문법, 현재 버전에 대응하는 새 릴리스 노트 경로를 검사했습니다.
- 기존 APK의 체크섬·버전·번들 웹 자산·비밀 파일 제외·Android 리소스 패키징 검사와 공개 파일 보안 패턴 검사를 통과했습니다. APK를 다시 빌드하거나 새 기기 검사를 수행한 변경은 아닙니다.

Converted 37 Markdown files as GFM and checked 212 local references and 10 image references/alt texts; no broken local links. Confirmed all 10 version notes were moved and eight earlier note bodies were preserved. Parsed workflow YAML, checked every Bash block's syntax and resolved the current version's note path. Existing APK checksum/version/assets/private-file exclusions/resource packaging and the public-file heuristic scan passed. This documentation update does not represent a new APK build or device test.
