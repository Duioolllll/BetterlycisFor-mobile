# 플레이리스트

재생 화면의 **플레이리스트** 버튼에서 내 음악과 Spotify 목록을 선택합니다.

## 내 음악

- **음악 파일 추가**에서 휴대폰의 음악 파일을 선택합니다. 목록 안에서 추가하면 해당 목록에 곡이 추가됩니다.
- **M3U 불러오기**에서 `.m3u` 또는 `.m3u8`을 선택한 다음, 해당 음악 파일이 들어 있는 폴더를 선택합니다. 선택한 폴더와 하위 폴더만 검색합니다.
- M3U의 순서를 유지합니다. 파일을 찾지 못하거나 같은 이름이 여러 곳에 있어 구별할 수 없는 곡, 인터넷 스트림 항목은 건너뛰고 개수를 표시합니다. UTF-8, UTF-16 BOM 및 Windows 한국어 인코딩을 읽습니다.
- 선택한 폴더에 음악과 이름이 같은 `.lrc` 파일이 있으면 해당 곡의 가사를 자동으로 불러옵니다. 예를 들어 `Album/one.wav`와 `Album/one.lrc`를 같은 폴더에 둡니다.
- 곡을 누르면 앱이 직접 재생합니다. 화면이나 알림에서 재생·일시정지·이전·다음·위치 이동을 사용할 수 있고, 목록에서 전체 반복·한 곡 반복·무작위를 선택할 수 있습니다. 검색과 목록에서 곡 빼기도 지원합니다.

음악 자체는 원래 위치에 남고, 앱은 Android 파일 선택기에서 사용자가 허용한 접근 권한과 목록만 저장합니다. 파일 이동·삭제 또는 권한 해제 시 다시 선택해야 할 수 있습니다. 한 목록은 최대 5,000곡, 저장 목록은 최대 100개입니다. 파일 형식의 실제 재생 가능 여부는 기기의 Android 미디어 코덱에 따릅니다.

## Spotify

**Spotify → Spotify 연결**을 누르고 브라우저에서 로그인한 후 앱으로 돌아옵니다. 플레이리스트를 선택해 곡을 살펴보고, 곡 또는 **Spotify에서 열기**를 누르면 Spotify에서 해당 항목을 엽니다. Spotify의 음원은 Spotify 앱에서 재생합니다.

공식 Spotify API는 플레이리스트의 곡 목록을 본인이 소유하거나 공동 작업자인 경우에만 제공할 수 있습니다. 목록에 표시되는 다른 플레이리스트는 Spotify에서 직접 열 수 있습니다. 개발 모드의 Spotify 앱은 소유자의 Premium 계정과 등록된 사용자에게만 동작하며, 일반 사용자 전체에 제공하려면 Spotify의 별도 확장 권한이 필요합니다. 계정 접근이 거부되면 개발자 대시보드의 사용자 등록과 앱 상태를 확인하세요.

로그인은 Spotify의 Authorization Code with PKCE를 사용합니다. 앱 비밀번호나 Client Secret은 받지 않습니다. 사용자 토큰은 Android Keystore의 키로 암호화해 앱 내부에 저장하고, **Spotify 연결 해제** 시 삭제합니다. 곡·플레이리스트 정보는 Spotify로 연결되는 화면에만 표시합니다.

Spotify 실제 계정 로그인과 제3자 음악 앱은 사용자 기기에서 별도 확인이 필요합니다. 이 프로젝트의 Android 자동 검사에는 합성 문서와 오디오, 가짜 토큰, 인증 콜백이 사용됩니다.

## 출처

- [BetterLyrics v1.3.527.0의 M3U 가져오기](https://github.com/jayfunc/BetterLyrics/blob/v1.3.527.0/src/BetterLyrics.DotNet/BetterLyrics.Core/ViewModels/MusicGalleryPageViewModel/MusicGalleryPageViewModel.Commands.cs)와 [재생목록 적용](https://github.com/jayfunc/BetterLyrics/blob/v1.3.527.0/src/BetterLyrics.DotNet/BetterLyrics.Core/ViewModels/MusicGalleryPageViewModel/MusicGalleryPageViewModel.Data.cs)을 기준으로 Android 파일 권한에 맞춰 구현했습니다.
- [Android 문서 접근](https://developer.android.com/training/data-storage/shared/documents-files), [백그라운드 미디어 재생](https://developer.android.com/media/platform/mediaplayer/background)
- [Spotify PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow), [Redirect URI](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri), [플레이리스트 곡](https://developer.spotify.com/documentation/web-api/reference/get-playlists-items), [개발 모드](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)
