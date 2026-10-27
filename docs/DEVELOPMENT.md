# 개발환경과 구조 — 0.5.0

## 확인된 기술 스택

| 항목 | 프로젝트/실행 환경에서 확인한 값 |
| --- | --- |
| 언어 | Java 8 source/target, JavaScript(ECMAScript, 별도 컴파일러 없음), HTML5, CSS, Python 3 |
| Android UI | Android SDK Activity/Service + 로컬 WebView, DOM/CSS/WebGL. React/Flutter/Compose 없음 |
| Android | minSdk 26(Android 8.0), compileSdk/targetSdk 36(Android 16) |
| JDK | OpenJDK 17.0.21+9, 실제 수동 빌드에 사용 |
| SDK 도구 | Build Tools 36.0.0: AAPT2, D8, zipalign, apksigner |
| Gradle | 설정 파일은 Android Gradle Plugin 8.10.1. Gradle 실행기/Wrapper는 저장소에 없고 이번에 실행 안 함 |
| Python / Node | 실제 환경 Python 3.12.14 / Node.js 24.19.0 |
| 테스트 | org.json 20260814(test 전용, SHA-256 고정); JDK assertions; Playwright 1.62.1 / Chromium: 브라우저 검사 56개 묶음 통과 |
| 런타임 라이브러리 | Android 내장 org.json, java.net, javax.crypto. 외부 Android AAR/JAR 없음 |
| 아이콘 | Bootstrap Icons 1.13.1, MIT |
| 분석 도구 | 기존 기록 REA 6.7.0, ILSpyCmd 11.1.0; 이번 릴리스에서 재실행 안 함 |
| 실제 OS | x86_64 Linux 클라우드 환경 |

`tools/build.py`가 이번 APK의 재현 경로다. Gradle 버전은 **실행 확인값 없음**이며 AGP 버전과 혼동하지 않는다. SDK·도구는 설치자가 공식 배포처에서 준비해야 한다. APK에 SDK/JDK/REA/ILSpy/Playwright/QA용 글꼴은 포함되지 않는다. Android 기기의 System WebView·시스템 글꼴에 따라 실제 표현과 성능이 달라진다.

## 폴더와 책임

- `app/src/main/AndroidManifest.xml`: 패키지, SDK, Activity, 알림 리스너, 오디오 공유 foreground service, 권한.
- `app/src/main/java/io/betterlyrics/mobile/`: 아래 네이티브 모듈.
- `app/src/main/assets/web/`: index.html, style.css, app.js, ambient.js, appearance.js/css, theme.css. 외부 CDN 의존성 없음.
- `app/src/main/assets/licenses/`, `LICENSE`, `THIRD_PARTY_NOTICES.md`: 라이선스.
- `app/src/main/res/`: 앱 아이콘·테마.
- `tests/`: 합성 데이터 기반 JVM/공급자 회귀 테스트.
- `tools/`: Python 빌드/JVM 테스트와 JS 화면 테스트/로컬 미리보기.
- `docs/`: 기술 문서·다국어 설치·검증 기록.
- `downloads/`: 검증한 공개 APK와 체크섬. `dist/`, `build/`, `.signing/`은 Git 제외.
- `.github/workflows/release.yml`: 이미 서명한 APK를 검증하여 Release에 게시. 개인 서명 키는 CI에 전달하지 않는다.

| 모듈 | 책임 |
| --- | --- |
| MainActivity | WebView 초기화, 권한/파일 선택, 제한된 JS 브리지, 재생 제어, lifecycle |
| LyricsListener | 시스템이 바인딩하는 알림 리스너, 활성 세션/콜백 관리, 플로팅 가사 |
| AppState | 세션 선택 상태·곡 세대·시계·설정·캐시 작업·UI snapshot |
| LyricsRepository / MatchScore | HTTPS, 공급자 검색·선택, 유효 가사 캐시, 한국어 별칭 비교 |
| LyricParser / LyricTextPolicy | 포맷 파싱, 시간/단어 모델, 중국어 보조 정보/마스킹 정책 |
| ArtworkResolver | 비동기 커버 후보, HTTPS/허용 URI, 이미지 크기 제한·축소 |
| AudioEngine / AudioCaptureService / AudioSignal | 동의 기반 소리 입력, 상태, 주파수·비트 분석 |
| IoSupport | 크기·시간·취소 제한 입력과 호환 hex 변환 |
| UiAppearance | 시각 설정 JSON 검증과 정규화 |
| app.js / appearance.js / ambient.js | 시계·가사·탐색, 프리셋/커스텀, 그라데이션·소리 효과 |

## 네트워크와 권한

앱의 UI는 번들된 로컬 자산이다. HTTP는 Java 작업 스레드에서 수행하고 UI 스레드에 결과를 전달한다. `LyricsRepository` 공급자 풀은 2개, `AppState` 네트워크 풀은 2개, 커버 풀은 1개다. 곡/검색/커버의 세대 번호를 검사해 늦은 응답을 버린다. 요청별 제한은 [API_REFERENCE.md](API_REFERENCE.md)에 있다.

알림 접근은 다른 앱의 미디어 세션을 읽기 위해 필요하다. SYSTEM_ALERT_WINDOW는 플로팅 가사만, RECORD_AUDIO는 Visualizer/재생 오디오 캡처만 필요하다. 마이크 AudioSource를 열지 않는다. Android 10 이상 대체 캡처는 매번 시스템 MediaProjection 동의를 받아 시작하고 화면을 벗어나면 종료한다. 서비스는 START_NOT_STICKY이며 MediaProjection callback으로 종료를 처리한다.

## 운영체제별 준비와 재현 상태

| OS | 설치 경로 | 실제 검증 |
| --- | --- | --- |
| Linux | 배포판 패키지/공식 Git, JDK 17, Python 3, Android Studio SDK Manager에서 API36·36.0.0·platform-tools 설치 | Linux 수동 debug/release 빌드 및 자동 테스트 실행 |
| Windows | Git for Windows, JDK17, Python3, Android Studio; SDK는 보통 `%LOCALAPPDATA%\Android\Sdk`; PowerShell 환경변수 설정 | `.exe`, 경로 구분, classpath 처리를 코드 검토했지만 실행 미검증 |
| macOS | 공식 Git 또는 Xcode CLI 도구, JDK17, Python3, Android Studio; SDK는 보통 `~/Library/Android/sdk` | POSIX 경로 코드 검토만, 실행 미검증 |

세 환경 모두 [BUILD.md](BUILD.md)의 Python 수동 빌드를 사용하도록 작성되어 있다. SDK 플랫폼과 Build Tools는 각 OS용이어야 한다. Windows/macOS에서 성공했다고 단정하지 않는다. ARM 호스트에서 도구 실행 호환성도 별도 확인 대상이다.

## 로컬 실행과 디버깅

[BUILD.md](BUILD.md)의 debug APK → `adb install -r` → 기기에서 알림 접근 → 음악 재생 순서로 확인한다. 로그를 수집할 때 곡 제목·앱 알림 등 개인 정보는 공유 전에 삭제한다. 웹 미리보기는 `npm run dev -- --host 127.0.0.1 --port 4173`; 데모 데이터로 화면만 실행하며 실제 Android 권한·미디어·오디오를 시험하지 않는다.

Android 16은 API36 컴파일, edge-to-edge insets/IME 처리, API33 뒤로 가기, API29 오디오 캡처 분기와 foreground service 권한 선언을 검토했다. 이는 설치/재생/백그라운드 안정성 인증이 아니다. 기기/에뮬레이터 미연결로 실제 Android 버전별 실행은 [VALIDATION.md](VALIDATION.md)에 미검증으로 남긴다.


0.4.3 서비스 변경·측정은 [SERVICE_OPTIMIZATION.md](SERVICE_OPTIMIZATION.md), 실제 release/설치 검사는 [VALIDATION.md](VALIDATION.md)와 Release의 JSON 보고서를 따른다. SDK 36/JDK17로 실제 릴리스 빌드를 실행했으며, Gradle·Windows/macOS 실행이나 실제 음악 앱의 기기 동작을 이번에 검증했다는 뜻은 아니다.
