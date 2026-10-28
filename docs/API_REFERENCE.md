# API reference — 0.9.0

이 문서는 현재 모바일 앱의 호출 경로를 설명한다. 입력 제한/취소/블로킹 시간의 범위는 [서비스 최적화 기록](SERVICE_OPTIMIZATION.md)을 따른다.

기준: `LyricsRepository.java`, `ArtworkResolver.java`, `LyricsListener.java`, `AppState.java`, `MainActivity.java` 및 [helper 정적 분석 기록](HELPER_ANALYSIS.md). 2026-10-22. 인증정보를 포함한 예시는 제공하지 않는다.

## 분류

| API / 제공자 | 분류 | 현재 APK |
| --- | --- | --- |
| Android NotificationListener / MediaSession / MediaController / MediaMetadata / PlaybackState | 공식 Android SDK | 음악 감지, 상태, 메타데이터, 재생 제어 |
| Android Visualizer / AudioPlaybackCapture / MediaProjection | 공식 Android SDK | 선택적 기기 내 소리 분석 |
| Android Storage Access Framework / ContentResolver | 공식 Android SDK | 사용자가 선택한 가사, 허용된 커버 URI |
| LRCLIB search | 제공자가 공개 문서화한 공개 API | 온라인 가사 검색 |
| NetEase Cloud Music EAPI | **비공식·역설계 프로토콜**, 안정성/배포 허가 미확인 | 온라인 검색·가사 |
| Kugou | **비공식·역설계 프로토콜**, 안정성/배포 허가 미확인 | 검색·KRC 가사 |
| QQ Music, NetEase WEAPI / 구형 검색 | **비공식·역설계 분석 대상** | 분석만 수행, APK 호출 안 함 |
| Android ↔ WebView `Android` 브리지 | 이 프로젝트 내부 API | 로컬 자산만 사용 |

Apple Music 앱의 Android 미디어 세션을 감지할 수 있다는 것은 Apple Music 가사 API를 사용한다는 뜻이 아니다. Spotify는 선택적으로 공식 Web API의 로그인, 플레이리스트 읽기와 활성 Spotify 기기의 재생 제어를 사용하며, MusicKit과 Spotify 음원 다운로드는 사용하지 않는다. 재생 제어에는 Premium 및 `user-modify-playback-state` 권한이 필요하다. 로그인은 PKCE, 토큰은 Android Keystore 암호화를 사용한다. [플레이리스트 사용 및 제한](PLAYLISTS.md).

## LRCLIB

`GET https://lrclib.net/api/search?track_name=<URL-encoded title>&artist_name=<URL-encoded artist>`

제목·정규화한 가수를 전송한다. 응답이 없으면 괄호/feat. 등을 정리한 제목으로 한 번 더 검색한다. 공개 문서는 [lrclib.net/docs](https://lrclib.net/docs)이며 API 키 없이 접근하는 방식이다. API의 정확한 호출 제한 수치를 코드가 가정하지 않는다. 현재 앱은 `/api/get`, `/api/publish`를 호출하지 않는다.

응답은 JSON 배열이다. 소비하는 필드는 `id`, `trackName`, `artistName`, `albumName`, `duration`(초), `syncedLyrics`(LRC 또는 null), `plainLyrics`(문자열 또는 null)이다. 최대 40개 후보만 읽고 밀리초로 변환한다. 원문이 없는 후보는 제외하며 동기화 가사가 없으면 일반 텍스트를 사용한다.

```json
[{"id":123,"trackName":"Example","artistName":"Artist","albumName":"Album","duration":180.0,"syncedLyrics":"[00:01.00]Example line","plainLyrics":"Example line"}]
```

위 값은 설명용 합성 데이터다. 가사 저작권과 API 서버 소프트웨어 라이선스는 별개다. 공개 API 접근 가능성이 모든 가사 재배포 권한을 뜻하지 않는다.

## NetEase EAPI — 비공식

공개 서비스 주소이며, helper의 정적 코드에서 확인한 요청 형식을 Java로 재구현했다. 내부 비밀 API나 계정 자격 증명을 획득한 것이 아니다. 공식 개발자 계약·안정성 보장·가사 재배포 허가는 확인되지 않았다.

| 작업 | 메서드 / 주소 | 변환 전 JSON |
| --- | --- | --- |
| 검색 | POST `https://interface.music.163.com/eapi/cloudsearch/pc` | `s`=제목+가수, `type`="1", `limit`="20", `offset`="0", `total`="true" |
| 가사 | POST `https://interface.music.163.com/eapi/song/lyric/v1` | `id`, `cp`="false", `lv/kv/tv/rv/yv/ytv/yrv`="0", `csrf_token`="" |

Content-Type은 `application/x-www-form-urlencoded; charset=UTF-8`이다. 본문은 `params=<hex>`이며 JSON·`/api/...` 경로·MD5 및 AES/ECB/PKCS5Padding 기반 EAPI 변환을 사용한다. 코드의 고정 포맷 상수는 사용자 비밀 키가 아니다. 이 문서에는 포맷 키 바이트, 인증 쿠키, 서명 키를 싣지 않는다. 빈 `csrf_token`은 계정 토큰이 아니다.

검색 응답은 `result.songs[]`: `id`, `name`, `artists` 또는 `ar` 배열의 `name`, `album` 또는 `al`의 `name`, `duration` 또는 `dt`(밀리초), `alia/alias/tns/transNames` 별칭을 소비한다. 최대 20개 후보를 읽는다.

가사 응답의 `lrc`, `yrc`, `tlyric`, `romalrc`, `ytlrc`, `yromalrc` 객체에서 `lyric` 문자열을 읽는다. YRC가 유효하면 실제 단어 타이밍을 사용한다. 한국어 LRC와 로마자 YRC의 불일치, 범위를 벗어난 시간, 마스킹 정도를 비교해 LRC로 돌아갈 수 있다. 서버의 성공 코드만으로 유효 가사라고 판정하지 않으며 파싱 결과를 검사한다.

## Kugou — 비공식

`mobilecdn.kugou.com/api/v3/search/song`에서 곡·가수 검색 후 응답의 파일 해시로 `lyrics.kugou.com/search` 후보를 조회한다. 곡명·가수·길이가 맞는 후보의 ID와 accesskey로 `lyrics.kugou.com/download`의 KRC를 받고, base64·KRC 압축을 풀어 단어별 시간과 포함된 보조 가사를 읽는다. 해시·ID 형식과 응답 크기를 제한하며 accesskey를 문서나 로그에 저장하지 않는다. 2026-10-28 한 곡의 실제 검색·후보·다운로드 응답을 앱 파서에 통과시켜 단어 시간 정보를 확인했다. 모든 곡이나 국가에서 제공된다는 뜻은 아니다.

## 타임스탬프와 선택

LRC 시간은 밀리초로 정규화한다. QRC 단어는 절대 시작, KRC 단어는 줄 시작에 대한 상대 시작, YRC 단어는 해당 포맷의 절대 시작을 사용한다. TTML과 Enhanced LRC 등 로컬 포맷도 `LyricParser`가 같은 줄/단어 모델로 변환한다. 실제 시간의 유무를 별도로 보존한다.

자동 선택은 메타데이터 점수·버전·길이·언어 별칭을 비교하고, 동기화/덜 마스킹된 후보를 선호한다. NetEase와 Kugou는 일반적으로 상위 적격 후보 3개, 마스킹이 남으면 최대 6개를 조회한다. 직접 선택은 사용자 판단을 유지한다.

## 호출 흐름과 오류

1. 미디어 세션 메타데이터 → 곡 키/세대 번호 → 캐시 조회.
2. 공급자 설정에 따라 LRCLIB/NetEase/Kugou를 순차 또는 병렬 검색.
3. 후보 점수 → 필요한 가사 조회 → 파서/텍스트 정책 → 유효 결과 저장.
4. 현재 곡 세대와 일치하는 결과만 UI로 전달. 실패 시 오류 안내, 직접 검색/파일 불러오기 제공.

네이티브 HTTPS 요청이므로 WebView CORS 우회용 서버가 필요 없다. 연결/읽기 타임아웃 각각 8초, 최대 본문 3 MiB, 읽기 루프 20초 경과·중단 검사(대기 중 읽기 타임아웃은 별도)다. 404는 빈 결과다. 429 또는 503의 `Retry-After`가 유효하고 5초 이하일 때만 그 시간을 기다려 한 번 재시도한다. 더 긴 지연, 잘못된 헤더, 그 밖의 비200 응답은 실패로 처리한다. 자동 리다이렉트와 무제한 재시도는 없다. 여러 후보를 조회하는 전체 검색은 한 번의 타임아웃보다 오래 걸릴 수 있다. 캐시는 최대 200개 JSON, 실패/구형 정책 캐시는 재사용하지 않는다.

커버 HTTP는 미디어 앱이 제공한 HTTPS URL을 사용한다. 리다이렉트도 HTTPS만 허용하고 최대 4회 시도, 연결/읽기 각각 5초, 본문 8 MiB로 제한한다. `content:` / `android.resource:`는 Android 권한에 따른 ContentResolver 경로다. 특정 비밀 이미지 엔드포인트가 없다.

## 내부 브리지

`Android.getBootstrap()` → JSON snapshot; `Android.elapsedRealtime()` → 단조 시계 ms; `Android.command(action, JSON.stringify(data))` → UI 스레드 작업. 네이티브가 `App.receive`, `App.audio`, `App.searchResults`를 호출한다. snapshot은 `track`, `lyrics`, `settings`, `permissions`, `sources`, `volume`을 포함한다. `setAppearance`는 `{appearance:{...}}`를 받고 허용된 열거형·범위·boolean·6자리 색상만 SharedPreferences에 저장한다. 임의 HTML/CSS 실행 API가 아니다. 원격 페이지 탐색과 파일/콘텐츠 접근은 WebView에서 비활성화되어 있다.

## 분석만 한 API

| 서비스 | 공개 경로와 요청/응답 요약 | 제한 |
| --- | --- | --- |
| QQ 검색 | POST `https://u.y.qq.com/cgi-bin/musicu.fcg`; JSON `req_1`의 `music.search.SearchCgiService / DoSearchForQQMusicDesktop`, query/search_type/page_num/num_per_page; 곡 후보 | helper의 정적 분석 |
| QQ 가사 | POST `https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg`; form version/miniversion/lrctype/musicid; XML `content/contentts/contentroma` 및 QRC | APK에 다운로드/해독 모듈 없음 |
| NetEase 구형/WEAPI | 구형 `/api/search/get/web`와 POST `https://music.163.com/weapi/song/lyric?csrf_token=`; id/os/lv/kv/tv/rv → LRC/번역/발음 | 과거 helper의 경로, 현재 APK 호출 안 함 |

이 경로들의 현재 서버 응답·오류 코드·계정 요구·호출 한도는 이번 릴리스에서 검증하지 않았다. 분석 기록만으로 공개 이용 허가를 주장하지 않는다.

## 이용 조건 및 개인정보

[NetEase 서비스 약관](https://st.music.163.com/official-terms/service)은 이번 환경에서 본문을 가져오지 못했다. 따라서 비공식 EAPI 이용/배포 조건 검토는 **미완료**다. 공급자 약관·가사 권리 확인은 서비스 사용/재배포 전에 별도로 필요하다. 앱 GPL 라이선스는 외부 가사·앨범 이미지의 권리를 부여하지 않는다. LRCLIB 또는 로컬 가사만 선택할 수 있다.

가사 검색 시 곡 제목과 가수(공급자별 요청은 위 명세), 인터넷 연결에 따른 IP가 공급자에 전달된다. 음악 계정 로그인·사용자 식별자·쿠키는 전송하지 않는다. 오디오 분석 값은 기기에서만 처리하며 녹음 파일을 저장하거나 업로드하지 않는다. 네트워크 연결에는 Android 기본 TLS 검증을 사용한다.
