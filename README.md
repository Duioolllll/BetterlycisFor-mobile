# BetterLyrics Mobile

[English](README.en.md) · [한국어 설치 안내](docs/INSTALL.ko.md) · [English installation](docs/INSTALL.en.md) · [中文安装说明](docs/INSTALL.zh-CN.md)

BetterLyrics의 음악 감지·가사 검색·동기화 동작을 Android에 맞게 구현한 **비공식 이식 시험판**입니다. 원작은 [Zhe Fang / BetterLyrics contributors](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0)이며 GPL-3.0을 따릅니다. 공식 BetterLyrics 또는 Apple의 배포물이 아닙니다.

## 다운로드와 설치

**[BetterLyrics-Mobile-0.3.0.apk 다운로드](https://github.com/sdcom1214/BetterlycisFor-mobile/raw/refs/heads/main/downloads/BetterLyrics-Mobile-0.3.0.apk)** · Android 8.0 이상 · ABI 공통 APK · versionCode 7

1. 위 APK를 내려받아 설치합니다. 시스템에서 요청하면 내려받은 브라우저/파일 앱의 **이 출처 허용**을 확인합니다.
2. 앱의 오른쪽 위 **··· → 음악 감지**에서 BetterLyrics의 알림 접근을 허용합니다.
3. 사용하는 음악 앱에서 노래를 재생하고 돌아옵니다. 여러 앱이 있으면 오른쪽 아래 목록 버튼으로 **재생 앱 선택**을 엽니다.
4. 선택 사항으로 **플로팅 가사** 또는 가운데 파형 버튼의 사운드 반응 배경을 연결합니다.

자세한 권한 설정, Play Protect 차단 안내, 업데이트와 싱크 문제 해결은 [한국어 설치 안내](docs/INSTALL.ko.md)를 확인하세요. 앱 화면은 현재 한국어이며 영어·중국어 문서는 설치 안내 번역입니다.

## 주요 기능

- Android MediaSession 음악 감지, 앨범 커버, 재생/일시정지·이전/다음·탐색·미디어 음량 조절
- LRCLIB / NetEase 가사 검색, 후보 직접 선택, 곡 정보·버전·길이 확인, 로컬 캐시
- 줄/단어 타이밍, 재생 진행도에 따른 가사 흐름, 탐색 미리보기, 곡별 싱크 보정
- LRC / Enhanced LRC / ESLRC / TTML, 디코딩된 QRC·KRC·YRC, 바이너리 KRC 가져오기
- 앨범색 유체 그라데이션, 선택적 사운드 반응 배경, 가로/세로 배치, 플로팅 가사
- 중국어 보조 번역·음역 숨김, 원문 보존, 마스킹 없는 가사 후보 우선 선택

음악은 다른 앱에서 재생합니다. 연결·탐색·커버는 해당 음악 앱이 제공하는 정보와 기능에 따라 달라집니다. 정확한 단어 타이밍이 없으면 기본 설정에서 줄 단위로 표시하며, 제공처에서 지운 단어를 추측하지 않습니다. 캡처가 제한된 음악 앱은 사운드 반응을 지원하지 않을 수 있습니다. Windows 플러그인, QQ/Kugou/Apple Music 온라인 가사 소스, Apple Music Sing 보컬 제거는 포함하지 않습니다. 암호화된 QRC는 지원하지 않습니다.

## 권한과 데이터

음악 감지에는 알림 접근이 필요합니다. 이 구현은 선택한 음악 앱의 미디어 세션과 일치하는 음악 알림 정보만 사용하며 일반 메시지 본문을 저장·전송하지 않습니다. 가사 검색 시 곡 제목·가수·앨범·길이를 선택한 제공처로 전송하고, HTTPS 커버 주소는 해당 이미지 서버에서 내려받습니다. 계정 로그인이나 API 키는 필요하지 않습니다.

사운드 반응은 선택 사항입니다. Android가 오디오 권한을 요청할 수 있지만 구현은 마이크 입력을 열지 않습니다. 기본 Visualizer 또는 Android 10 이상에서 사용자 동의를 받은 AudioPlaybackCapture로 기기 안에서 소리 특성을 계산하고 저장·전송하지 않습니다. 화면을 벗어나면 분석을 종료합니다.

## 소스 받기와 동기화

```sh
git clone https://github.com/sdcom1214/BetterlycisFor-mobile.git
cd BetterlycisFor-mobile
git pull --ff-only origin main
```

수정한 파일을 확인하고 필요한 파일만 추가한 다음 커밋·push합니다. push에는 저장소 쓰기 권한과 GitHub 인증이 필요합니다.

```sh
git status
git add README.md
git commit -m "docs: update installation guide"
git push origin main
```

## 빌드

Python 3, **JDK 17**, **Android SDK Platform 36 + Build Tools 36**을 준비합니다. 다음 경로는 Gradle 의존성 다운로드 없이 로컬 SDK를 사용합니다.

```sh
python3 tools/build.py --sdk /absolute/path/to/android-sdk
```

결과는 `dist/BetterLyrics-Mobile-0.3.0.apk`입니다. 첫 빌드에서 `.signing/development.p12`를 생성합니다. 공개 저장소에는 서명키가 포함되지 않으므로 새로 빌드한 APK의 서명은 위 다운로드 APK와 다릅니다. 기존 설치본 위에 업데이트하려면 같은 패키지와 같은 키가 필요합니다. 생성한 키를 개인적으로 보관하고 공개 저장소에 올리지 마세요. 개발용 키는 운영 배포용으로 재사용하지 마세요.

Android Studio에서 프로젝트를 열어 Gradle을 동기화할 수도 있습니다. 검증한 빌드 경로는 위 Python 스크립트이며 Gradle Wrapper는 포함하지 않습니다.

## 검증과 문서

```sh
python3 tools/test.py
python3 tools/network-test.py --sdk /absolute/path/to/android-sdk --json-jar /path/to/json-20250517.jar --offline
```

선택적 브라우저 검사는 Playwright/Chromium을 별도로 준비한 뒤 실행합니다. 필요하면 `BETTERLYRICS_CHROME`으로 실행 파일을 지정합니다.

```sh
node tools/ui-smoke.cjs
node tools/flow-smoke.cjs
node tools/player-smoke.cjs
```

공개 저장소 준비 과정에서 Java·저장소 오프라인 회귀 검사 **176개**와 기존 APK v2·v3 서명 검증을 통과했습니다. 기존 0.3.0 기록에는 브라우저 모의 네이티브 브리지의 UI 29개·흐름 9개·플레이어 7개 그룹 및 APK 빌드 검증이 있습니다. 앱 코드를 바꾸지 않은 이번 문서·배포 정리에서는 브라우저 검사를 다시 실행하지 않았습니다. **실제 Android 기기 설치·권한·음악 앱 연동은 아직 검증하지 않았습니다.**

- [검증 기록](docs/VALIDATION.md) · [0.3.0 변경](docs/CHANGES-0.3.0.md)
- [원본 소스·배포물 분석](docs/ORIGINAL_ANALYSIS.md) · [UI 참고 자료와 차이](docs/APPLE-UI-REFERENCE.md)
- [라이선스](LICENSE) · [제3자 고지](THIRD_PARTY_NOTICES.md) · [APK 체크섬](downloads/SHA256SUMS)

Apple의 폰트·이미지·코드는 포함하지 않습니다. Android 시스템 글꼴과 MIT 라이선스 Bootstrap Icons를 사용합니다.
