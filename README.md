<div align="center">

# BetterLyrics Mobile

### 지금 듣는 음악, 가사와 함께.

음악 앱의 재생 흐름을 따라가는 Android 가사 플레이어.<br>
큰 가사, 앨범색 그라데이션, 내 취향대로 바꾸는 화면.

[![Release 0.7.0](https://img.shields.io/badge/release-0.7.0-8b7cf8?style=flat-square)](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/tag/v0.7.0)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square)](docs/INSTALL_KO.md)
[![GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)

### [↓ Android APK 다운로드 · v0.7.0](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/BetterLyrics-Mobile-0.7.0.apk)

**2026.10.28 배포** · Android 8.0 이상 · 2.3 MiB · versionCode 15

[릴리스 / Assets](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/latest) · [설치 안내](docs/INSTALL_KO.md) · [변경 기록](changelog/README.md) · [English](README.en.md) · [简体中文安装](docs/INSTALL_ZH.md)

![BetterLyrics Mobile 가로 가사 화면](docs/images/lyrics-wide.webp)

</div>

> **처음 설치하나요?** 위의 **APK 다운로드**를 누르세요. Source code ZIP은 Android 설치 파일이 아닙니다. 다운로드가 열리지 않으면 [대체 경로](https://github.com/Duioolllll/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.7.0.apk)를 이용할 수 있습니다.

## 음악에 맞춰 흐르는 가사

| 가사에 집중 | 음악과 연결 | 나만의 화면 |
| --- | --- | --- |
| 진행도 기반 이동, 현재 줄 강조, 제공되는 단어 타이밍, 곡별 싱크 보정 | Android MediaSession 감지, 앨범 아트, 재생·탐색·음량 제어 | 화면 프리셋, 글꼴·크기·정렬·여백, 앨범색 그라데이션, 선택적 소리 반응 배경 |

LRCLIB / NetEase 가사 검색, 직접 후보 선택, 로컬 가사 가져오기·캐시, 플로팅 가사를 지원합니다. 한국어 곡의 제목 별칭·버전·길이를 비교하며, 단어 타이밍이 없는 가사는 줄 단위로 표시합니다. 앱 화면은 현재 한국어입니다.

<details>
<summary><strong>세로 화면과 지원 기능 더 보기</strong></summary>

<p align="center">
  <img src="docs/images/lyrics-phone.webp" alt="작은 앨범 커버와 큰 현재 가사를 표시하는 세로 화면" width="280">
</p>

- **가사:** LRC / Enhanced LRC / ESLRC / TTML, 디코딩된 QRC·KRC·YRC와 바이너리 KRC 가져오기. 탐색 미리보기, 현재 가사로 복귀, 곡별 시간 보정.
- **화면:** 기본 스타일 / 가사 집중 / 앨범 중심 프리셋. 글자 굵기·흐림·밝기·커버·색상·컨트롤 자동 숨김을 저장하고 즉시 적용.
- **플레이리스트:** 휴대폰 음악·M3U 가져오기, 내장 재생, Spotify 목록 조회와 Spotify에서 열기.
- **위젯:** 홈 화면 가사·재생 제어, 배경·크기·커버 설정.
- **배경:** 기본 앨범색 그라데이션, 선택적 실제 소리 반응, 강도 조절, 동작 줄이기와 WebGL 미지원 시 대체 표시.
- **음악 연동:** 호환 MediaSession을 제공하는 음악 앱에서 정보와 재생 상태를 받습니다. 커버·가사·탐색·오디오 공유는 음악 앱과 제공처에 따라 제한될 수 있습니다.

</details>

스크린샷은 실제 번들 웹 UI에 합성 곡 정보와 직접 작성한 예시 가사를 넣은 브라우저 캡처입니다. Android 실기기 캡처가 아닙니다. 실행 중 앨범 아트는 음악 앱에서 가져옵니다.

## 3단계로 시작하기

1. **[v0.7.0 APK 다운로드](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/BetterLyrics-Mobile-0.7.0.apk)** → 파일을 엽니다. 필요하면 해당 브라우저·파일 앱에만 **이 출처의 앱 설치 허용**을 설정합니다.
2. 앱 실행 → **··· → 음악 감지**에서 알림 접근을 허용합니다. 음악 앱에서 노래를 재생한 뒤 돌아옵니다.
3. **··· → 화면 꾸미기**에서 가사·배경·배치를 선택합니다. 소리 반응과 플로팅 가사는 선택 기능입니다.

**설치 안내:** [한국어](docs/INSTALL_KO.md) · [English](docs/INSTALL_EN.md) · [简体中文](docs/INSTALL_ZH.md)

Play Protect 경고 내용을 확인하고, 위험 앱으로 차단되면 설치를 중단하세요. 설치 후 출처별 설치 권한은 해제할 수 있습니다. 업데이트는 같은 패키지·서명이 필요하며, 앱 삭제 시 설정·가사 캐시가 사라질 수 있습니다. 설치 실패 원인과 권한별 설명은 설치 안내에서 확인하세요.

## 이번 릴리스 · 0.7.0

- **내 음악:** 휴대폰 음악과 M3U/M3U8 목록을 불러와 직접 재생하고, 검색·추가·제거·이름 변경과 반복·무작위를 사용할 수 있습니다.
- **Spotify:** 계정의 플레이리스트와 곡을 살펴보고 Spotify에서 엽니다. Spotify 음원은 Spotify 앱에서 재생합니다.
- **가사와 재생:** M3U 폴더의 같은 이름 LRC 가사를 읽으며 화면·알림·위젯에서 로컬 재생 상태를 표시합니다.

[플레이리스트 사용 방법](docs/PLAYLISTS.md) · [0.7.0 릴리스 노트](changelog/0.7.0.md) · [원본 기능 대응 현황](docs/PARITY.md) · [모든 버전](changelog/README.md)

0.4.0에는 확인된 설치 오류가 있습니다. **현재 배포인 0.7.0을 받으세요.** 0.4.2는 소스 기록만 있으며 별도 APK 릴리스가 없습니다.

<details>
<summary><strong>다운로드 정보 · SHA-256 · 실제 검증 결과</strong></summary>

| 항목 | 값 / 결과 |
| --- | --- |
| 파일 | `BetterLyrics-Mobile-0.7.0.apk` · 2,419,799 bytes |
| 패키지 / 버전 | `io.betterlyrics.mobile` · 0.7.0 (15) |
| Android | 최소 8.0 (API 26) · compile / target 16 (API 36) |
| APK 구조 | Universal · 네이티브 `.so` 없음 |
| 오프라인 검사 | 256개 통과 · 기준 오디오 2,073프레임 출력 동일 · 패키징 5개 통과 |
| 서명 / 빌드 | v2/v3 · 기존 인증서 일치 · ZIP 정렬 · 같은 환경의 2회 빌드 바이트 일치 |
| 브라우저 UI | 7개 suite · 70개 검사 묶음 통과 |
| Android 16 AVD | 위젯·폰트·플레이리스트·설치·업데이트 검사 · 첨부 보고서 참조 |
| 공개 파일 | 게시 후 APK 재다운로드·SHA-256 검증 단계 |

```text
003ab9d703937e1c155226bfeb6a44c1e9ab98ef67b7405b15157107b38202ef
```

[SHA256SUMS](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/SHA256SUMS) · [RELEASE_VALIDATION.json](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/RELEASE_VALIDATION.json) · [UI_VALIDATION.json](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/UI_VALIDATION.json) · [WIDGET_VALIDATION.json](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/WIDGET_VALIDATION.json) · [PLAYLIST_VALIDATION.json](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/PLAYLIST_VALIDATION.json) · [INSTALL_VALIDATION.json](https://github.com/Duioolllll/BetterlycisFor-mobile/releases/download/v0.7.0/INSTALL_VALIDATION.json) · [실행 로그](https://github.com/Duioolllll/BetterlycisFor-mobile/actions/workflows/release.yml) · [검증 범위](docs/VALIDATION.md)

</details>

## 문서와 개발

| 사용자 안내 | 개발·기술 문서 |
| --- | --- |
| [한국어 설치·업데이트·문제 해결](docs/INSTALL_KO.md) | [개발환경·구조·OS별 준비](docs/DEVELOPMENT.md) |
| [English installation](docs/INSTALL_EN.md) | [Debug / Release 빌드·서명·ADB](docs/BUILD.md) |
| [简体中文安装指南](docs/INSTALL_ZH.md) | [공식·비공식 API](docs/API_REFERENCE.md) |
| [릴리스·변경 기록 모음](changelog/README.md) | [REA 분석·Android 재구현](docs/REVERSE_ENGINEERING.md) |
| [검증과 미검증 항목](docs/VALIDATION.md) | [보안·라이선스 검토](docs/SECURITY_AND_LICENSES.md) |

**기술 스택:** Java 8 source/target · JDK 17 · Android SDK 36 / Build Tools 36.0.0 · WebView HTML/CSS/JavaScript + WebGL · Python 3 빌드 도구. 외부 Android 런타임 프레임워크 의존성은 없습니다. AGP 8.10.1 설정이 있지만 Gradle 실행은 검증하지 않았습니다.

```sh
git clone https://github.com/Duioolllll/BetterlycisFor-mobile.git
cd BetterlycisFor-mobile
python3 tools/build.py --variant debug --sdk /path/to/Android/Sdk
python3 tools/test.py --sdk /path/to/Android/Sdk
```

개인 서명 키 없이 Debug APK를 만들 수 있습니다. Release 빌드에는 외부에서 제공하는 개인 서명 키가 필요합니다. [빌드 안내](docs/BUILD.md)에서 환경변수와 명령을 확인하세요.

## 알려진 제한

- **실기기 미검증:** 실제 휴대폰·다른 Android 버전·음악 앱별 한국어 싱크·앨범 아트·오디오 캡처·절전/재연결·장시간 안정성은 이번 릴리스에서 검증하지 않았습니다. Linux JVM 성능 측정은 휴대폰 성능·배터리 수치가 아닙니다.
- **제공처 의존:** NetEase는 비공식 API이며 이용·배포 조건 확인이 남아 있습니다. LRCLIB 일시적 HTTP 503을 관찰했습니다. 마스킹된 원문을 추측해 복원하거나 없는 타임스탬프를 만들지 않습니다.
- **Spotify:** 개발 모드에서는 앱 소유자의 Premium 계정과 등록된 사용자만 연결할 수 있습니다. 실제 계정 로그인은 별도 확인이 필요합니다.
- **기능 범위:** QQ / Kugou / Apple Music 온라인 가사, 암호화 QRC, Windows 플러그인·Wallpaper Engine, Sing 보컬 제거는 포함하지 않습니다.
- **검증 범위:** 기존 개발용 인증서 계보를 유지합니다. Google 검토·Play Protect 통과를 의미하지 않습니다. Chromium에서 UI 검사 70개 묶음을 통과했으며, 모의 Android 브리지로 검사했습니다.

## 출처와 라이선스

**독립적인 비공식 프로젝트**입니다. 원본 BetterLyrics, Apple 또는 음악 서비스가 배포하는 공식 앱이 아닙니다. BetterLyrics의 소스·배포물 분석을 참고해 Android에 맞게 구현했습니다.

원작: [Zhe Fang / BetterLyrics contributors · v1.3.527.0](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0). Android 재구현: BetterLyrics Mobile contributors, 2026. [GPL-3.0](LICENSE), 보증 없음. 배포 APK에 대응하는 소스·빌드 도구는 릴리스 태그에 있습니다. 개인 서명 키는 포함하지 않습니다. Bootstrap Icons 1.13.1은 MIT입니다. [제3자 고지](THIRD_PARTY_NOTICES.md).

외부 가사·앨범 이미지·상표의 권리는 각 권리자에게 있으며 소스 라이선스가 콘텐츠 재배포 권한을 부여하지 않습니다. 원본 패키지와 디컴파일된 DLL 소스는 이 저장소에 배포하지 않습니다.
