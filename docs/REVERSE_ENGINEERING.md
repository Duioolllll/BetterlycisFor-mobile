# 역설계 및 Android 재구현 기록

대상: BetterLyrics Mobile 0.4.0 / 2026-10-22의 분석 기록. 현재 구현은 [기능 대응 현황](PARITY.md)을 따른다. 독립적인 비공식 프로젝트이며 원작자·Apple·음악 서비스의 공식 배포물이 아니다.

## 근거와 수행 범위

원본은 [jayfunc/BetterLyrics v1.3.527.0](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0), 커밋 `3b04c19e371818d820dfc7197789168d70a1543b`이다. 기존 저장소의 [원본 분석](ORIGINAL_ANALYSIS.md), [helper 분석](HELPER_ANALYSIS.md), [데이터 흐름](source-dataflow-review.md), [렌더링](source-rendering-review.md)을 교차 검토했다. 이번 릴리스에서는 Android 소스와 원본의 LyricsStyleSettings, LyricsEffectSettings, LayoutProfile 및 README 스크린샷을 확인했다.

기존 분석 기록에는 다음 수행 내역이 있다.

1. Core / Sdk / WinUI3 소스를 읽고 음악 감지 → 검색 → 파싱 → 동기화 → 렌더링 경로를 추적했다.
2. REA 6.7.0으로 공식 ZIP 인벤토리를 작성하고 ZIP → MSIXBundle → x64 MSIX → DLL을 정적으로 추출했다.
3. PE/CLI 메타데이터, CIL 멤버·호출 관계와 네이티브 경계를 조사했다.
4. ILSpyCmd 11.1.0으로 Core 6개 / WinUI3 4개 주요 타입을 디컴파일해 태그 소스와 의미를 대조했다. Lyricify helper 23개 선택 타입도 별도로 분석했다.

REA의 목적은 배포 바이너리의 실제 구성과 호출 경로를 소스의 설명과 대조하는 것이었다. 대상 Windows 앱이나 DLL을 실행한 동적 분석은 아니다. 기존 문서의 `ev_…` 식별자는 당시 분석 기록 식별자이며 공개 다운로드 링크가 아니다. 원시 REA 세션·패키지·디컴파일 파일은 이 공개 저장소에 없고, 이번 0.4.0 검토에서 추출/디컴파일을 다시 실행하지 않았다. 따라서 과거 기록의 수치와 해시를 이번에 새로 측정한 값이라고 주장하지 않는다. 배포물 SHA-256은 ORIGINAL_ANALYSIS.md에 보존되어 있다.

## 구조와 재구현

| 원본의 책임 | Android 구현 | 차이 |
| --- | --- | --- |
| SMTC 음악 세션 | LyricsListener: NotificationListenerService, MediaSessionManager, MediaController | Android가 접근을 허용한 세션만 감지 |
| 메타데이터 비교·가사 공급자 | MatchScore, LyricsRepository | LRCLIB / NetEase만 온라인 지원; QQ/Kugou/Apple 온라인 공급자 미포함 |
| 가사 모델·포맷 파서 | LyricParser, LyricTextPolicy | Java 모델; 실제/추정 시간·동시 보컬 구분 |
| LyricsSynchronizer / Animator | AppState의 시계 + app.js의 활성 줄/스크롤 앵커 | Android 단조 시계, JS 프레임 보간 |
| Win2D / ComputeSharp 캔버스 | WebView DOM/CSS, reactive.js WebGL | 원본 셰이더·Windows 코드를 실행하지 않음 |
| WASAPI 오디오 분석 | AudioEngine, AudioSignal, AudioCaptureService | Visualizer 또는 사용자 동의 AudioPlaybackCapture |
| 원본 레이아웃 편집기 | UiAppearance + appearance.js/css | 3개 프리셋과 제한된 설정, 원본의 자유 격자 편집기는 미구현 |

직접 작성한 Android 코드는 Java, HTML, CSS, JavaScript이다. Windows DLL을 APK에 포장하거나 호출하지 않는다. 원본의 매칭 가중치와 포맷 의미를 참고했으며 플랫폼 수명주기·브리지·레이아웃·배경·설정 저장은 모바일 구현이다. 디컴파일된 소스 파일 자체는 배포하지 않는다.

## 음악 상태와 가사 시간

`LyricsListener`는 활성 세션 변경과 MediaController 콜백을 등록하고, 연결 종료 시 해제한다. 0.4.0은 알림 접근 권한이 남아 있을 때 재바인딩을 요청한다. Android가 다시 연결할지와 제조사 절전 정책의 영향은 실기기 검증 대상이다.

`AppState`는 곡 키, 재생 위치·속도·상태, 커버와 가사 세대 번호를 관리한다. PlaybackState 갱신 시각과 `SystemClock.elapsedRealtime()`로 현재 위치를 계산한다. JS는 전달 지연과 속도를 반영해 프레임 사이 위치를 보간한다. 일시정지는 보간을 멈춘다. 곡별 오프셋은 표시 시간에 더하고, 가사 탭으로 탐색할 때는 역으로 뺀다. 탐색 미리보기는 실제 시계를 변경하지 않으며 완료/취소/늦은 샘플/거절을 별도로 처리한다.

동시에 활성인 코러스 줄과 스크롤 중심 줄은 다르다. 연속 모드는 줄 진행도에 맞춰 이동하며, 줄 고정 모드와 시스템 동작 줄이기도 제공한다. 시간 없는 텍스트에 정확한 동기화를 꾸며 넣지 않는다. 단어 타이밍이 없는 줄은 기본적으로 줄 단위 표시다.

## 검색·한국어·앨범 아트에서 해결한 문제

- 제목/가수/앨범/길이 가중치 .30/.30/.10/.30을 사용한다. 자동 선택은 추가적인 제목·가수·버전·길이 검사 및 70점 기준을 적용한다. 원본 60점 기준과 다르다.
- 한국어 별칭과 정규화를 비교하고, 한국어 LRC가 있는데 YRC가 로마자만 포함하거나 시간이 범위를 벗어나면 LRC로 돌아간다.
- 원문 욕설을 자체 마스킹하지 않는다. 덜 마스킹된 실제 응답을 선호하지만 제공처가 지운 단어를 추측해 복원하지 않는다. 중국어 보조 트랙·크레딧을 제거하는 기존 텍스트 정책을 유지한다.
- 곡 세대 검사로 이전 검색/이미지 응답이 새 곡에 덮어쓰지 못하게 한다. 실패 가사를 영구 캐시하지 않고 재검색은 캐시를 우회한다.
- 커버는 미디어 메타데이터, URI, 동일 세션·제목의 알림 대형 아이콘을 순서대로 사용한다. 크기 제한·축소·HTTPS·지연 도착 갱신을 적용한다. 음악 앱이 제공하지 않는 커버는 만들지 않는다.
- 이번 릴리스는 HTTP 본문 읽기에 중단/시간 제한을 추가했다. 연결·읽기 타임아웃과 여러 후보 시도로 검색이 수십 초 걸릴 가능성은 남는다.

## UI 근거와 한계

원본은 기본 왼쪽 정렬, 주변 글자 약 30% 선명도, 중심 50% 앵커, 흐림/빛 번짐/스크롤 효과와 앨범·가사 배치 프리셋을 갖는다. 이를 모바일 설정에 반영했다. 가로에서는 커버/가사 분할, 세로에서는 작은 커버와 가사 중심, 가사 집중 프리셋에서는 가운데 정렬을 사용한다. Apple Music을 참고한 기존 진행도·단어 강조·앨범색 배경도 유지한다.

스크린샷은 정적 형태의 근거이며 원본 애니메이션과 프레임 단위로 같다는 증거가 아니다. Windows 글꼴·GPU 셰이더·플러그인·Wallpaper Engine·Sing 보컬 제거는 재현하지 않는다. 온라인 API 가용성, 모든 한국어 곡의 타이밍, 실기기 서비스/권한/발열/배터리는 자동 테스트만으로 보장할 수 없다. 현재 결과와 미검증 항목은 [VALIDATION.md](VALIDATION.md)를 따른다.
