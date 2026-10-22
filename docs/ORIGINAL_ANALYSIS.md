# BetterLyrics 원본 분석 및 Android 이식 근거

분석 기준: 공식 배포본 v1.3.527.0, 소스 태그 v1.3.527.0 (3b04c19e371818d820dfc7197789168d70a1543b). Windows 앱을 실행한 결과가 아닌 정적 소스·패키지·CIL·디컴파일 분석이다.

## 수행한 순서
1. 원본 저장소의 Core / Sdk / WinUI3 계층과 음악 감지, 가사 검색, 파싱, 동기화, 렌더링의 호출 흐름을 읽었다.
2. 공식 GitHub release ZIP을 내려받아 REA 6.7.0으로 인벤토리 및 압축 해제를 수행했다.
3. ZIP → MSIXBundle → x64 MSIX → 앱 DLL 순으로 패키지를 해제했다. 별도 설치·대상 프로그램 실행은 하지 않았다.
4. REA로 PE/CLI 메타데이터, CIL 메서드, 호출 관계, 네이티브 경계를 읽었다.
5. ILSpyCmd 11.1.0으로 Core 6개/WinUI3 4개 주요 타입을 C#으로 디컴파일하고 원본 태그 코드의 핵심 불변 조건과 대조했다. 가사 공급자 helper도 별도로 분석했다.

## 배포물 식별
| 대상 | SHA-256 |
|---|---|
| 공식 ZIP | ee9f4d0ced20a6d0e1dbfc3c980b3566d27892410a5fabafb77adb5f015005ba |
| x64 MSIX | 8c01a475ee544678d8a0f6d107b13dd4d2ddf121a3f83c4bf9bbb7f0c162fc30 |
| BetterLyrics.Core.dll | 172c98c08209e8128f7a019210078f472dd612f396556e0a15201abb7530995e |
| BetterLyrics.WinUI3.dll | 80ea9d3de7fd1f2f59607004e253c1b77cd7adc6b8696073f55a1d86f65ade38 |
| BetterLyrics.Sdk.dll | 3896dde78babb38ef5ba14dea0d7e3dc6fe4250947a7fcb553859c8cac3881c5 |

## 근거 기록
- ZIP inventory: ev_35b64419e182cd768c8218dc610b121a60d148cc6ae4edd86db92f0f5017e7a6
- ZIP extraction: ev_56881958fab97c331435cf2362dcb09a84370c9580447a6a5c96370ded1ade40
- Core identity: ev_6d4ed5e3ded470e340bda688188c4d1f4ccb331bf71d4d4539cdf761e10bcdaa
- Core CIL/member inventory: ev_6495793f074a2e2fd52c9f9b946f6181575f2397df05af1637f2d3a9e60712e4
- WinUI identity: ev_12e330d3385ecae198cf834f557ca7df4ac40c20d86decec3902ef63cb6c918b
- Core 인벤토리: 755 타입, 5,928 메서드, 37,828 정적 호출 엣지. 이 수치는 코드 전체를 실행·검증했다는 의미가 아니다.

## 원본에서 확인하고 유지할 동작
| 원본 | 확인한 동작 | Android 대응 |
|---|---|---|
| MediaManager/MediaSession/GsmtcService | SMTC에서 메타데이터·상태·위치 수신 | NotificationListenerService + MediaSessionManager + MediaController |
| MetadataComparer | 제목 .30, 가수 .30, 앨범 .10, 길이 .30; Jaro-Winkler | 같은 가중치로 Java 이식 |
| LyricsSearchService | 순차/최고일치 검색, 기준 60점, 공급자별 캐시 | 동일 개념을 독립적인 Android 네트워크 계층으로 구현 |
| LyricsContentParser | 실제/추정 음절 구분, 배경보컬, 화자, 번역·발음 | 원본 정보를 보존하는 모바일 가사 모델 |
| LyricsSynchronizer/Animator | 스크롤 기준 한 줄과 실제 활성 가사들의 상태 분리 | 별도 스크롤 앵커와 시간 구간별 하이라이트 |
| NowPlayingCanvas | 프레임 사이 내부 시계, 공급자 위치로 보정, 오프셋 | Android 단조 시계 + 재생속도·PlaybackState 갱신시각 |
| Win2D/ComputeSharp | 텍스트 도형·마스크·GPU 셰이더 | 모바일 전용 렌더링 백엔드 |

## 발견한 이식 주의점과 의도적인 개선
- 최신 dev는 공식 태그보다 가사 캔버스가 크게 달라져 있어 두 버전을 혼합하지 않는다.
- 코러스/화자가 다른 동시 가사를 번역으로 합치지 않는다. 실제 타임스탬프가 없는 줄을 정확한 단어 동기화로 표시하지 않는다.
- 원본은 일시정지 중 오프셋 반영이 늦을 수 있어 모바일에서는 멈춘 상태의 탐색/오프셋 변경에도 표시 시간을 갱신한다.
- 곡 교체 중 이전 HTTP 결과가 뒤늦게 도착하지 않도록 세대 번호를 확인한다.
- 원본의 실패 캐시 무기한 보관을 피하고, 유효한 가사만 보관한다. 수동 재검색은 캐시를 우회한다.
- LRCLIB의 첫 결과만 선택하던 원본을 개선하여 후보의 메타데이터 점수를 비교한다. 이는 의도적인 개선이며 원본 코드 그대로의 동작은 아니다.
- WinUI/WinRT/Direct2D/WASAPI, Windows DLL 플러그인과 작업표시줄/Wallpaper Engine 통합은 별도 플랫폼 구현이 필요하다.
- 디컴파일 결과는 재구성된 C#이다. 선택한 경로의 의미를 대조했으며 전체 바이너리의 재현 빌드 일치를 주장하지 않는다.

세부 파일·행 근거는 함께 제공하는 source-dataflow-review.md와 source-rendering-review.md에 있다.
