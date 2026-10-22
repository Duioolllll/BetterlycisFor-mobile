# BetterLyrics Mobile

다른 음악 앱의 재생 상태에 맞춰 가사를 보여 주는 **독립적인 비공식 Android 앱**입니다. BetterLyrics의 소스·배포물 분석을 참고해 Android에 맞게 구현했습니다. 원본 BetterLyrics, Apple 또는 음악 서비스가 배포하는 공식 앱이 아닙니다.

**최신 배포: 0.4.0 · Android8.0 이상 · target Android16 · versionCode8**

**[APK 다운로드](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/download/v0.4.0/BetterLyrics-Mobile-0.4.0.apk)** · [최신 Release / Assets](https://github.com/sdcom1214/BetterlycisFor-mobile/releases/latest) · [다운로드 대체 경로](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.4.0.apk) · [SHA-256](downloads/SHA256SUMS)

**설치:** [한국어](docs/INSTALL_KO.md) · [English](docs/INSTALL_EN.md) · [简体中文](docs/INSTALL_ZH.md) · [English README](README.en.md)

| 세로 가사 화면 | 가로 화면 |
| --- | --- |
| ![세로 가사 화면](docs/images/lyrics-phone.webp) | ![가로 가사 화면](docs/images/lyrics-wide.webp) |

위 이미지는 실제 번들 웹 UI를 합성 곡 정보·직접 작성한 예시 가사로 실행한 브라우저 캡처입니다. 실기기 캡처나 음악 서비스의 실제 앨범이 아닙니다. APK는 음악 앱이 제공하는 앨범 아트를 사용합니다.

## 주요 기능

- Android MediaSession 음악 감지, 앨범 아트, 재생/일시정지·이전/다음·탐색·음량 제어.
- LRCLIB / NetEase 검색, 직접 후보 선택, 한국어 별칭·버전·길이 비교, 로컬 캐시/가사 파일 가져오기.
- 실제 단어 타이밍과 줄 타이밍 구분, 진행도 기반 가사 이동, 탐색 미리보기, 곡별 시간 보정.
- 원본 스타일 / 가사 집중 / 앨범 중심 프리셋과 **UI 커스터마이징**: 배치, 정렬, 글꼴 스타일·크기·굵기, 여백, 흐림, 밝기, 커버, 색상, 자동 숨김.
- Apple Music에서 참고한 가사 강조·앨범색 그라데이션, 선택적 실제 소리 반응 배경, 동작 줄이기·WebGL 미지원 대체 표시.
- LRC / Enhanced LRC / ESLRC / TTML, 디코딩된 QRC·KRC·YRC, 바이너리 KRC 가져오기, 플로팅 가사.

## 빠른 설치

1. Release의 **Assets → BetterLyrics-Mobile-0.4.0.apk**를 받습니다. 소스 ZIP은 설치 파일이 아닙니다.
2. APK를 열고 필요할 때만 해당 브라우저/파일 앱의 **이 출처 설치 허용**을 설정합니다. 앱 이름/권한을 확인합니다.
3. 실행 후 **··· → 음악 감지**에서 알림 접근을 허용하고 음악 앱에서 노래를 재생합니다.
4. **··· → 화면 꾸미기**에서 화면을 바꿉니다. 소리 반응/플로팅 가사는 선택 기능입니다.

Play Protect가 위험 앱으로 차단하면 경고를 확인하고 설치를 중단하세요. 보안 기능을 강제로 끄는 절차는 제공하지 않습니다. 설치 후 출처별 설치 권한은 다시 해제할 수 있습니다. 업데이트에는 같은 패키지·서명이 필요하며, 삭제하면 설정/가사 캐시가 사라질 수 있습니다. 자세한 내용은 다국어 설치 안내를 따르세요.

## 검증과 알려진 문제

[전체 검증 결과](docs/VALIDATION.md) · [0.4.0 변경 사항](docs/RELEASE-0.4.0.md) · [보안·라이선스 검토](docs/SECURITY_AND_LICENSES.md)

- Linux에서 debug/release 빌드, release v2/v3 서명·SHA-256·기존 인증서 일치, 동일 환경 두 번의 APK 바이트 일치를 확인했습니다.
- 오프라인 JVM 190개 검사, 브라우저 UI/흐름/플레이어/커스터마이징 회귀를 실행했습니다.
- 한국어 3곡의 실제 제공처에서 한글·동기화 가사를 가져왔지만 청취에 맞춘 실기기 싱크 검증은 아닙니다. LRCLIB 일시적 HTTP503도 관찰했습니다.
- **실제 Android 기기/에뮬레이터 테스트는 미수행**입니다. Android16 설치·권한·MediaSession·절전/재연결·오디오 공유·발열/성능은 확인이 필요합니다.
- 앱 UI는 현재 한국어입니다. 커버·가사·탐색은 음악 앱과 공급자에 의존합니다. 마스킹된 원문을 추측해 복원하지 않으며, 정확한 단어 타이밍이 없으면 기본적으로 줄 단위로 표시합니다.
- NetEase는 비공식 API이며 이용/배포 조건 확인이 끝나지 않았습니다. QQ/Kugou/Apple Music 온라인 가사, 암호화 QRC, Windows 플러그인/Wallpaper Engine, Sing 보컬 제거는 포함하지 않습니다.
- 릴리스는 기존 개발용 인증서 계보를 유지합니다. Google 검토/Play Protect 통과나 모든 기기 호환성을 보장하지 않습니다.

## 기술 스택과 개발

Java8 source/target, Android SDK36, JDK17, HTML/CSS/JavaScript WebView + WebGL, Python3 수동 빌드. 외부 Android 프레임워크 의존성은 없습니다. AGP8.10.1 설정은 있지만 Gradle 실행은 검증하지 않았습니다.

```sh
git clone https://github.com/sdcom1214/BetterlycisFor-mobile.git
cd BetterlycisFor-mobile
python3 tools/build.py --variant debug --sdk /path/to/Android/Sdk
python3 tools/test.py
```

개인 서명 키 없이 debug APK를 만들 수 있습니다. 배포 APK와의 업데이트 호환성은 별도입니다.

- [개발환경·모듈·OS별 안내](docs/DEVELOPMENT.md)
- [설치·debug/release 빌드·서명·ADB·재현 검증](docs/BUILD.md)
- [공식/비공식 API 명세](docs/API_REFERENCE.md)
- [REA 분석·역설계와 재구현](docs/REVERSE_ENGINEERING.md)

## 원본·라이선스

원작: [Zhe Fang / BetterLyrics contributors, v1.3.527.0](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0). 수정·Android 재구현: BetterLyrics Mobile contributors, 2026. [GPL-3.0](LICENSE), 보증 없음. 배포 APK에 대응하는 소스·빌드 도구는 릴리스 태그에서 제공하며 개인 서명 키는 포함하지 않습니다. Bootstrap Icons1.13.1은 MIT입니다. [제3자 고지](THIRD_PARTY_NOTICES.md).

외부 가사·앨범 이미지·상표의 권리는 각 권리자에게 있으며, 소스 라이선스가 해당 콘텐츠의 재배포 권한을 부여하지 않습니다. 원본 패키지와 디컴파일된 DLL 소스는 이 저장소에 배포하지 않습니다.
