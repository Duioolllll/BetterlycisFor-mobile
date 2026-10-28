# BetterLyrics 기능 대응 현황

비교 기준은 원본 BetterLyrics 정식 태그 [v1.3.527.0](https://github.com/jayfunc/BetterLyrics/tree/v1.3.527.0)입니다. Windows와 Android의 서비스·권한·음악 앱 API가 다르므로 동일한 기능이라도 플랫폼에 맞춰 구현합니다. 아래 표는 전체 1:1 구현 완료를 뜻하지 않습니다.

| 원본 기능 | 모바일 현황 |
| --- | --- |
| 현재 음악 감지, 재생 상태와 위치 | Android MediaSession 연동; 음악 앱별 제공 범위에 의존 |
| 음악 파일과 M3U 플레이리스트, 재생 순서 | 파일 선택, M3U와 같은 폴더의 LRC, 내장 재생, 전체·한 곡 반복과 무작위 지원 |
| Spotify 재생 정보 | Android MediaSession으로 현재 곡 감지; 공식 API로 계정 플레이리스트 조회와 활성 Spotify 기기에서 목록·선택한 곡 재생 지원 |
| 동기화 가사, 번역·발음, 시간 보정 | LRC 등 여러 로컬 형식과 LRCLIB/NetEase 대응; 공급자가 제공하는 시간 정보에 의존 |
| 글꼴·레이아웃·배경, 플로팅 가사 | 모바일 화면 설정, 한글 글꼴, 오디오 반응 배경, Android 오버레이와 홈 위젯 |
| 음악 라이브러리의 앨범·아티스트별 보기, 재생목록 편집·내보내기 | 곡 검색·추가·제거와 이름 변경을 제공하며 앨범·아티스트별 전체 라이브러리와 내보내기는 미구현 |
| SMB/FTP 라이브러리 | 미구현 |
| QQ·Kugou·Apple Music 온라인 가사, 암호화 QRC | 미구현; 디코딩된 형식의 일부는 로컬 가져오기 가능 |
| Windows 플러그인·작업 표시줄·Wallpaper Engine·Spout | Windows 전용; Android 대응 미구현 |
| 통계·가사 카드·외부 번역 플러그인 | 미구현 |

원본 분석 근거는 [ORIGINAL_ANALYSIS.md](ORIGINAL_ANALYSIS.md), [source-dataflow-review.md](source-dataflow-review.md), [source-rendering-review.md](source-rendering-review.md)에 있습니다. Android 실기기와 타사 음악 앱·런처·절전 정책은 에뮬레이터 검사와 구분해 검증해야 합니다.
