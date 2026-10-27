# 개발자 설치·빌드

릴리스 0.6.0의 실제 빌드 경로는 **Python 수동 빌드**다. Android Studio용 Gradle 설정은 있지만 `gradlew`나 Gradle Wrapper는 제공하지 않으며 이 가이드는 존재하지 않는 Wrapper 명령을 사용하지 않는다.

## 1. 사전 요구사항과 Git

Git, **JDK17**, Python3, Android SDK platform **36**, Build Tools **36.0.0**, 설치/로그용 platform-tools(ADB)가 필요하다. UI 테스트만 Node.js와 Playwright가 필요하다. APK 자체에는 npm 패키지를 넣지 않는다.

- Windows: [Git for Windows](https://git-scm.com/download/win), JDK17, Python3, [Android Studio](https://developer.android.com/studio)를 설치한다. Python을 PATH에 추가한다.
- macOS: Git(또는 Xcode Command Line Tools), JDK17, Python3, Android Studio를 설치한다.
- Linux: 배포판의 Git/Python3/JDK17 또는 공식 배포본, Android Studio/Android command-line tools를 설치한다.

각 OS의 Android Studio SDK Manager에서 API36, Build Tools36.0.0, platform-tools를 설치한다. 공식 command-line tools가 이미 있다면 다음도 가능하다. SDK 라이선스 내용은 설치자가 확인한다.

```sh
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
git --version
java -version
python3 --version
```

Windows에서는 `sdkmanager.bat`, `python`을 사용해도 된다. 실제 빌드 검증은 Ubuntu에서만 수행했다. Windows/macOS는 설치 안내와 경로 처리만 검토했다.

## 2. Clone과 환경변수

```sh
git clone https://github.com/Duioolllll/BetterlycisFor-mobile.git
cd BetterlycisFor-mobile
git checkout v0.6.0
```

태그가 아직 배포되지 않은 경우 `main`에서 작업하되 커밋을 기록한다. Linux/macOS 예:

```sh
export JAVA_HOME=/absolute/path/to/jdk-17
export ANDROID_SDK_ROOT=/absolute/path/to/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

macOS SDK 기본 위치는 `$HOME/Library/Android/sdk`다. Windows PowerShell 예:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"
```

`.env.example`는 변수 설명용이며 자동 로딩하지 않는다. 실제 키/암호는 비공개 프로세스 환경에 설정한다. `local.properties`, `.env`, 서명 키를 커밋하지 않는다. 수동 빌드는 Python 표준 라이브러리와 SDK/JDK만 사용하므로 `pip install`/Gradle 의존성 다운로드가 없다.

## 3. Debug APK

```sh
python3 tools/build.py --variant debug --sdk "$ANDROID_SDK_ROOT"
```

Windows: `python tools/build.py --variant debug --sdk "$env:ANDROID_SDK_ROOT"`.

출력: `dist/BetterLyrics-Mobile-0.6.0-debug.apk`. `.signing/debug.p12`를 처음에 생성하고 이후 재사용한다. debug는 `debuggable=true`이며 공개 릴리스와 서명이 달라 덮어쓰기 업데이트가 안 될 수 있다.

## 4. Release APK와 개인 서명

새 배포자는 저장소 밖에 자신의 키를 만든다. 아래 명령은 암호를 대화형으로 묻는다. 개인 키와 암호를 백업하되 GitHub에 올리지 않는다.

```sh
keytool -genkeypair -keystore /private/path/release.p12 -storetype PKCS12 -alias mobile -keyalg RSA -keysize 3072 -validity 10000
```

기존 공개 APK의 업데이트는 **그 APK의 원래 키**가 필요하다. 새 키로 기존 사용자의 앱을 업데이트할 수 없다. 이 저장소에 공개 릴리스 키는 없다.

Linux/macOS(Bash, 입력이 화면/명령 기록에 남지 않는 예):

```sh
export BL_KEYSTORE=/private/path/release.p12
export BL_KEY_ALIAS=mobile
read -r -s -p 'Keystore password: ' BL_STORE_PASSWORD
export BL_STORE_PASSWORD
export BL_KEY_PASSWORD="$BL_STORE_PASSWORD"
python3 tools/build.py --variant release --sdk "$ANDROID_SDK_ROOT"
unset BL_STORE_PASSWORD BL_KEY_PASSWORD
```

키 암호가 저장소 암호와 다르면 `BL_KEY_PASSWORD`도 별도로 읽어 설정한다. Windows PowerShell7에서는 `$env:BL_STORE_PASSWORD = Read-Host -MaskInput 'Keystore password'`처럼 입력하고 같은 변수 이름을 설정한다. 구형 PowerShell은 비공개 환경 관리 도구를 사용한다. 암호 값을 소스나 빌드 명령 문자열에 넣지 않는다.

출력은 `dist/BetterLyrics-Mobile-0.6.0.apk`, `.apk.sha256`, `build-info-release.json`이다. release는 `debuggable=false`; 키가 없으면 실패하며 새 키를 자동 생성하지 않는다. 빌드가 AAPT2 → Java8 bytecode → D8 → ZIP 정규화 → zipalign → apksigner 서명/검증을 수행한다. 버전은 Manifest에서 읽으며 app/build.gradle과 함께 변경해야 한다.

## 5. 기기 설치와 로그

개발자 옵션과 USB 디버깅을 기기에서 허용한 뒤:

```sh
adb devices
adb install -r dist/BetterLyrics-Mobile-0.6.0-debug.apk
adb shell am start -n io.betterlyrics.mobile/.MainActivity
adb logcat -c
adb logcat AndroidRuntime:E chromium:E '*:S'
```

필요하면 `adb logcat -d > device-log.txt`로 전체 로그를 비공개 저장하고 식별 정보를 지운 뒤 공유한다. 이번 로컬 환경에는 연결 기기가 없어 이 설치·로그 명령은 실행하지 않았다. 오류 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`은 서명, `VERSION_DOWNGRADE`는 versionCode, 파싱 오류는 손상된 APK/미지원 OS부터 확인한다. 앱 삭제는 설정·가사 캐시를 지운다.

## 6. 자동 테스트

```sh
python3 tools/test.py
python3 tools/network-test.py --sdk "$ANDROID_SDK_ROOT" --json-jar /path/to/json-20260814.jar --offline
python3 tools/appearance-test.py --json-jar /path/to/json-20260814.jar
```

테스트 JAR은 [org.json 20260814](https://repo1.maven.org/maven2/org/json/json/20260814/json-20260814.jar)을 별도 준비한다. APK에는 Android 내장 org.json을 사용한다. 실제 공급자 확인은 위 network-test에서 `--offline`을 제거하거나 `--korean`을 사용한다. 가사 본문을 로그에 출력하지 않지만 제목/가수 검색 요청은 공급자로 전송한다.

```sh
npm install --no-save --package-lock=false playwright@1.62.1
npx playwright install chromium
# 별도 터미널: npm run dev -- --host 127.0.0.1 --port 4173
export BETTERLYRICS_PREVIEW_URL=http://127.0.0.1:4173/
for suite in ui flow player appearance review customization; do node "tools/$suite-smoke.cjs"; done
```

기존 Chromium 실행 파일을 쓸 때 `BETTERLYRICS_CHROME`에 절대 경로를 지정한다. Linux에서는 한글 글꼴이 없으면 캡처에 네모가 나타날 수 있으므로 QA 호스트에 한글 글꼴을 설치한다. QA 호스트에 별도로 설치한 글꼴은 APK에 포함하지 않는다. 앱에는 오프라인 한글 글꼴 네 종류가 포함된다. 미리보기는 `npm run dev`이며 데모 전용이다.

## 7. 실패 해결

- API36/platform 또는 Build Tools36.0.0을 못 찾음: SDK Manager 설치 경로와 `--sdk`를 확인. 다른 API로 조용히 대체하지 않는다.
- `jdk.compiler`/keytool 없음: JRE 대신 JDK17을 설치하고 PATH/JAVA_HOME을 맞춘다.
- 실행 권한/CPU 오류: 현재 OS·아키텍처용 Android 도구인지 확인한다.
- 서명 오류: 기존 키 경로·alias·환경변수·암호를 확인한다. 키를 재생성해 덮어쓰지 않는다.
- 테스트 `org.json` 없음: 별도 테스트 JAR 경로를 지정한다.
- Gradle 경고/동기화 실패: 검증된 Python 경로를 사용한다. AGP8.10.1 설정만 존재하며 Gradle 빌드 성공을 보장하지 않는다.

## 8. 재현 검증과 배포

같은 커밋·JDK·SDK·Build Tools·서명 키/alias로 release를 두 번 빌드하고 첫 APK를 비공개 작업 폴더에 복사한 뒤 SHA-256을 비교한다. `tools/compare-apk.py first.apk second.apk`는 전체 파일과 서명 메타데이터를 제외한 ZIP payload를 비교한다. 새 개발자 키를 쓰면 전체 APK 해시는 달라지는 것이 정상이다. 플랫폼/JDK가 달라지면 payload도 다를 수 있으며 교차 OS bit-for-bit 재현은 검증되지 않았다.

```sh
python3 tools/compare-apk.py first.apk dist/BetterLyrics-Mobile-0.6.0.apk
```

공개 배포자는 APK·SHA256SUMS·`downloads/release.json`·릴리스 노트를 검토한 뒤 커밋한다. GitHub Actions는 키를 보관하지 않고 이미 서명된 APK의 해시·서명·버전을 검사한 후 Release에 첨부한다. 설정/권한 때문에 Actions가 거부되면 관리자 계정으로 workflow 또는 Release 업로드를 완료해야 한다. 기존 태그와 첨부 파일을 자동 덮어쓰지 않는다.

## APK installation packaging guard (0.6.0)

`python3 tools/apk-packaging-test.py --fixed-apk dist/BetterLyrics-Mobile-0.6.0.apk` checks the actual old failure and the fixed resource table. Android R+ targets require resources.arsc stored uncompressed and4-byte aligned. tools/build.py now checks the aligned and signed APK; tools/verify-release.py repeats the check before publication. APK signature/zipalign success alone did not detect the0.4.0 compressed table.

The CI release gate uses a dedicated disposable API36 emulator and `python3 tools/install-smoke.py --serial emulator-5554 --report /tmp/INSTALL_VALIDATION.json`. It tests only this app on that emulator, refuses physical-device serials, reproduces0.4.0 rejection and checks fresh install/start and in-place update/start from0.3.0 and0.4.1. Never run it on an emulator holding data you need.


## 0.6.0 배포 전 검사

```sh
python3 tools/release-preflight.py --sdk /path/to/Android/Sdk --report build/RELEASE_VALIDATION.json
```

현재 `downloads/release.json`에 지정한 APK로 233개 JVM 검사, 2073프레임 출력 동등성, 5개 패키징 검사, checksum/웹 자산/서명/기존 인증서/버전·SDK/공개 파일 검사를 실행한다. 별도 SDK/JDK와 org.json 테스트 JAR이 필요하며, 테스트 JAR은 누락 시 checksum을 검증해 내려받는다. 개인 서명 키는 필요 없다. GitHub release workflow는 Manifest의 버전/파일명에 맞게 동작하며 SDK 경로를 명시적으로 전달하고, 전용 Android16 AVD 설치 검증을 통과한 뒤 APK·SHA256SUMS·소스/브라우저/위젯/설치 검증 JSON을 게시한다. 이미 게시한 같은 태그의 자산을 자동 교체하지 않는다.

0.6.0에서는 HTTP 미리보기를 실행한 뒤 `ui`, `flow`, `player`, `appearance`, `review`, `customization` 브라우저 smoke를 실행합니다. 각 명령에 `BETTERLYRICS_PREVIEW_URL=http://127.0.0.1:4173/`를 지정하면 관리형 브라우저의 file URL 제한을 피할 수 있습니다. 게시 워크플로는 여섯 suite의 결과를 `UI_VALIDATION.json`으로 첨부하며 Android16 설치·업데이트 검사 전에 모두 통과해야 합니다.
