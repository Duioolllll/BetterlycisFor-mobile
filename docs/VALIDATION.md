# 0.5.0 — 2026-10-27

214 JVM checks, 2,073 bit-identical audio frames, five packaging cases and 56 browser check groups passed. Debug/release builds use JDK17/API36/Build Tools36.0.0. The release certificate matches the previous public APK. [Code review and re-review](CODE_REVIEW_0.5.0.md) · [Release notes](../changelog/0.5.0.md).

Publication requires the current APK's offline/signature checks, all five browser suites and Android16 fresh install/launch plus updates from 0.3.0/0.4.1/0.4.3/0.4.4. See that Release's RELEASE_VALIDATION.json, UI_VALIDATION.json and INSTALL_VALIDATION.json for actual CI results. Physical phones, other Android versions, live music-app integration and battery remain unverified.

---

# 0.4.4 — 2026-10-24

214 JVM checks, 2,073 bit-identical frames against 0.4.3, five packaging cases, same-certificate v2/v3 signing and two byte-identical local release builds passed. Android 16 fresh install and updates from 0.3.0/0.4.1/0.4.3 gate publication; actual reports are attached to the Release. Physical devices and battery behavior remain unverified.

[0.4.4 notes](../changelog/0.4.4.md) · [FFT measurements](performance/fft-2026-10-24.json)

---

# 0.4.3 릴리스 검증 — 2026-10-23

## 로컬에서 실제 확인한 항목

- 서비스/공급자/파서/정책/설정/오디오 회귀 214개와 APK 패키징 5개를 통과했다. 검사 실행 결과는 Release의 `RELEASE_VALIDATION.json`에 첨부한다.
- 기준 `8a12f2a`와 PCM/Visualizer/정지 2,073프레임 출력이 비트 단위 동일하다. [측정 원본](performance/service-io-2026-10-23.json)과 [최적화 검토](SERVICE_OPTIMIZATION.md)를 보존했다.
- 0.4.3 / versionCode11 / io.betterlyrics.mobile / min26 / target36, debuggable=false로 release 빌드했다.
- 원래 RSA3072 인증서 SHA256과 APK v2/v3 서명, resources.arsc STORED·offset2416·4바이트 정렬을 확인했다.
- 동일 환경·키로 두 번 빌드한 APK 바이트가 일치했다. 크기115650 bytes, SHA256 `2bd116afdc20d4b9b41c4af52ebcacff75d9cb2ab0960a053f147e30acfba078`.
- 공개 파일의 비밀 키·일반 인증정보/개인정보 패턴을 검사해 발견사항이 없었다. 휴리스틱 검사로 완전한 보안 감사를 의미하지 않는다.
- 웹 UI 자산은 변경하지 않았다. 브라우저 smoke는 Chromium이 없고 공식 다운로드 ZIP이 손상돼 재실행하지 못했다. 과거 브라우저 통과 기록을 이번 통과로 계산하지 않는다.

## Android 16 배포 gate

이번 APK의 통과 여부는 Release에 첨부한 `INSTALL_VALIDATION.json`과 해당 Actions 실행 결과를 따른다. 아래는 **게시 전에 모두 통과해야 하는 실제 검사**다.

1. 전용 API36 AVD에서 0.4.0을 설치해 resources.arsc 오류 거절 재현.
2. 0.4.3 신규 설치·버전 확인, MainActivity 시작·3초 후 프로세스 생존.
3. 0.3.0 설치 후 0.4.3 덮어쓰기 업데이트·버전 확인·시작.
4. 최신 공개 0.4.1 설치 후 0.4.3 덮어쓰기 업데이트·버전 확인·시작.
5. Release 게시 후 공개 URL에서 APK를 다시 다운로드해 SHA256 일치 확인.

0.4.2는 소스/메타데이터만 커밋됐으며 공개 Release/APK로 게시되지 않았다. 해당 소스 수정은 0.4.3에 포함된다. 다운로드 폴더의 SHA256SUMS는 실제 포함한 APK만 나열한다.

## 미검증 항목

실제 휴대폰·Android8–15·제조사 정책·Play Protect 판정, 실제 음악 앱의 MediaSession/한국어 청취 싱크/앨범 아트/오디오 캡처, 권한 흐름·장시간 안정성·발열/배터리·교차 OS 재현 빌드는 이번 테스트 범위가 아니다. Linux JVM 성능 개선율을 휴대폰 개선율로 간주하지 않는다. 이전 릴리스의 기기 설치 검증을 이번 APK의 검증으로 재사용하지 않는다.

[0.4.3 notes](../changelog/0.4.3.md) · [빌드 정보](build-info-0.4.3.json) · [설치](INSTALL_KO.md)

---

# 0.4.1 설치 수정 검증 — 2026-10-23

## 이번에 직접 확인한 항목

- 실제0.4.0 APK: resources.arsc가 DEFLATE(8), 데이터 offset2407. Android R+ 설치 조건 위반을 확인했다.0.3.0은 STORED(0), offset2380으로 조건을 충족했다.
-0.4.1 release: STORED(0), offset2412,4바이트 정렬. 압축/정렬을 서명 전후에 검사했다. APK v2/v3 서명과 원래 인증서 SHA256 일치를 확인했다.
- Java 파서/매칭/텍스트 정책/오디오 신호155개 검사 통과. 실제 이전 APK와 잘못된 ZIP fixture를 포함한 패키징5개 검사 통과.
- 같은 환경에서0.4.1을 두 번 빌드한 전체 APK와 payload 해시가 일치했다.
- SHA256: `1af5849e88074fa84688f31c2f2926df950bb55145d3eaf2f95d33bac14ff6fd`.
- UI 기능은 수정하지 않았다. 과거190개 JVM/50개 브라우저 검사는 아래0.4.0 기록이며, 이번에 전부 다시 수행했다고 주장하지 않는다.

## Android16 AVD 배포 조건

Release를 게시하기 전에 전용 API36 가상 기기에서 다음을 실행한다. 실제 통과/실패와 이전 오류 문구는 Release에 첨부하는 `INSTALL_VALIDATION.json` 및 해당 Actions 실행 기록을 따른다.

1.0.4.0이 resources.arsc 때문에 설치 거절되는지 확인.
2.0.4.1 신규 설치와 설치된 버전 확인.
3.MainActivity 시작과3초 후 프로세스 생존 확인.
4.0.3.0 설치 후 원래 서명으로0.4.1 덮어쓰기 업데이트 확인.

실제 사용자 휴대폰, Play Protect 판단, 모든 Android 버전/제조사, 다른 인증서의 기존 앱, 음악 세션/권한/오디오 캡처/장시간 안정성은 이 검사 범위가 아니다. 로컬 호스트에는 KVM과 연결 기기가 없어 AVD 검사는 GitHub Actions에서 수행하도록 구성했다.

## 과거 기록과 검증의 빈틈

아래0.4.0의 서명·체크섬·zipalign 검사는 실제로 통과했지만, 압축된 resources.arsc를 허용하는 검사들이었다. 따라서 설치 가능성을 증명하지 못했다.0.4.1은 저장 방식과 로컬 ZIP 데이터 offset을 독립적으로 검사하고 실제 PackageManager 설치를 배포 gate에 추가했다.

[Changelog](../CHANGELOG.md) · [0.4.1 notes](../changelog/0.4.1.md) · [패키징 결과](apk-packaging-qa-0.4.1.json) · [빌드 정보](build-info-0.4.1.json)

---

# 0.4.0 검증 결과

날짜: 2026-10-22. Ubuntu24.04.3 / JDK17.0.20 / SDK36 / Build Tools36.0.0. 아래는 이번 코드에서 실제 수행한 검증이다. 과거 0.1–0.3 보고서는 해당 버전의 JSON/CHANGES 파일에 보존한다.

## 실행한 검증

| 항목 | 실제 결과 | 한계 |
| --- | --- | --- |
| JVM 파서/매칭/정책/신호 | Parser79 + Math28 + TextPolicy19 + AudioSignal29 = 155 통과 | 합성 입력, Android 런타임 아님 |
| 실제 Repository 오프라인 회귀 | 21 통과 | HTTPS 대신 정해진 Transport 응답 |
| 네이티브 커스텀 설정 스키마 | 14 통과 | org.json JVM; SharedPreferences 기기 재시작 미검증 |
| UI | 29 검사 묶음, 360×740 / 412×915 / 812×375 / 1280×800 / 800×1280 | headless Chromium/모의 Android 브리지 |
| 진행도·탐색 | 9 검사 묶음 | 재생/일시정지/외부 탐색, preview/cancel/commit, 늦은 응답/거절, offset, 수동 읽기, 간주, 긴 줄, 동작 줄이기 |
| 플레이어 | 7 검사 묶음, 5 화면 크기 | 앨범/가사, 음량/고정음량, source/transport/import/overlay 브리지 |
| 커스터마이징 | 5 검사 묶음, 4 화면 크기 + 데모 재로드 | 프리셋/실시간 설정, 모달 focus, 초기화의 시계·offset 보존, 숨김·복원, 브라우저 저장 |
| debug/release 빌드 | 둘 다 성공 | 실제 설치는 안 함 |
| release 서명 | v2/v3, RSA3072, 기존 0.3.0 인증서와 일치 | 개발용 인증서 계보; Google 승인 아님 |
| 버전/패키지 | 0.4.0/code8, io.betterlyrics.mobile, min26/target36, debuggable=false | 정적 APK 메타데이터 |
| 재현성 | 동일 환경·같은 키로 2회 빌드한 전체 APK와 payload 일치 | 다른 OS/JDK/새 키는 미검증 |
| APK/공개 파일 | ZIP/웹자산/체크섬 검사, 개인 파일·일반 토큰/PII 패턴 검사 | 완전한 보안 감사는 아님 |

총 **190개 JVM 검사**, **50개 브라우저 검사 묶음**. 브라우저 예외는 0건. 구조화 결과: [UI](ui-qa-0.4.0.json), [flow](flow-qa-0.4.0.json), [player](player-qa-0.4.0.json), [appearance](appearance-qa-0.4.0.json), [APK 정보](build-info-0.4.0.json).

수동 클라우드 브라우저 미리보기 재연결은 연결 실패 후 브라우저 정책으로 막혀 완료하지 못했다. 별도 자동 테스트에서 이미 생성한 로컬 캡처를 검토했으며 이를 수동 브라우저 테스트 통과로 계산하지 않는다. 한글 QA 글꼴은 호스트에만 설치했고 APK에 포함하지 않았다.

## 이번 실제 공급자 요청

`tools/network-test.py --korean`이 실제 LyricsRepository로 요청한 결과:

| 검색 곡 | 최종 선택 | 행 수 | 확인 |
| --- | --- | --- | --- |
| 밤편지 / 아이유 | NetEase | 27 | 한글 포함, 시간 정보 있음 |
| 봄날(Spring Day) / 방탄소년단(BTS) | LRCLIB | 61 | 한글 포함, 시간 정보 있음 |
| Ditto / NewJeans | LRCLIB | 48 | 한글 포함, 시간 정보 있음 |

LRCLIB 검색에서 일시적 HTTP503을 관찰했다. 후속 실제 fetch에서는 공급자 결과를 얻었다. 이 검사는 가사 존재·한글·시간 구조를 확인하며 실제 음악과 청취 대조한 싱크 정확도를 증명하지 않는다. 모든 한국어 곡, 지역/계정/기간별 가용성은 미검증이다. 가사 본문은 공개 로그/문서에 싣지 않는다.

## 코드로 점검한 Android16 경계

API36으로 컴파일하고 systemBars/displayCutout/IME insets, API33 뒤로 가기, API29 AudioPlaybackCapture 분기, mediaProjection FGS type/권한, projection callback, START_NOT_STICKY, 앱 화면 이탈 시 분석 중지를 확인했다. 알림 서비스 종료 시 정리와 권한이 남은 경우 재바인딩 요청을 검토했다. 공식 참고: [Android16 target 변경](https://developer.android.com/about/versions/16/behavior-changes-16), [모든 앱 변경](https://developer.android.com/about/versions/16/behavior-changes-all), [MediaProjection](https://developer.android.com/media/grow/media-projection).

## 아직 검증하지 않은 항목과 수동 확인 절차

**ADB/실기기/에뮬레이터가 없으므로 Android8–16 설치·실행, 실제 강제 종료/ANR/무한 로딩/연결 해제 재현 여부를 확정할 수 없다.** 다음을 출시 후 기기별 확인해야 한다.

1. Android8, 10, 13, 14, 15, 16에서 설치/업데이트, 앱 열기/닫기, 회전, 글자 크기, 제스처/3버튼 내비게이션.
2. 알림 접근 허용/거부/취소, 앱/서비스 종료 후 재연결, 잠금·절전·제조사 배터리 정책, 음악 앱 전환.
3. Spotify/YouTube Music/Apple Music/삼성 뮤직의 실제 메타데이터·커버 URI·알림 아이콘·재생/탐색 capability 확인.
4. 한국어 원곡/라이브/리마스터 버전을 듣고 가사 줄/단어 시간, +/− 보정, 빠른 곡 변경을 대조.
5. Visualizer/오디오 권한/공유 동의, 캡처 금지 콘텐츠, 잠금/앱 이동 후 중지와 재동의, 발열·배터리·프레임 성능.
6. 앱을 완전히 종료/재실행한 후 SharedPreferences 복원, 플로팅 가사, 오프라인 캐시, 실제 불안정 네트워크.
7. Windows/macOS에서 BUILD.md 절차를 실행. 현재는 코드/안내 검토만 했다.

문제 보고 시 기기·Android·WebView·앱 버전, 재현 순서, 공급자/곡 버전, 개인정보를 제거한 로그를 첨부한다. Play Protect 통과/악성 여부는 이 테스트에서 판정하지 않았다.
