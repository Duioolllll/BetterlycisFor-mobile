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
