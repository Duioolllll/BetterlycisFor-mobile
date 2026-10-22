# 보안·라이선스 검토 — 0.4.0

2026-10-22. 공개 예정 소스/문서/이미지/APK에 대해 파일 목록·자격 증명 패턴·APK 내용과 브리지/HTTP 경계를 검토했다. 이는 독립 보안 감사, 악성 앱 검사 엔진, Google 인증을 대체하지 않는다.

## 공개 파일과 서명

`tools/audit-public.py`는 Git 추적/공개 예정 파일 전체에서 개인 키 확장자, PEM 개인 키 헤더, 일반적인 서비스 토큰/Bearer/이메일 패턴을 검사한다. APK는 `tools/verify-release.py`로 ZIP 무결성, 소스 웹 자산 일치, 개인 파일 제외와 해시를 확인한다. 이 휴리스틱으로 모든 형태의 비밀을 자동 검출할 수 있다고 주장하지 않는다.

- `.signing/`, `.env`, local.properties, 개인 키, 빌드 출력, 로그, 원본 패키지/ZIP은 Git 제외. `.env.example`에는 변수 이름과 빈 암호만 있다.
- 공개 릴리스 개인 키는 외부 비공개 경로에서 사용했고 APK/저장소/CI에 업로드하지 않았다. 소스에 있던 개발키 Gradle 암호 설정을 환경변수 방식으로 바꿨다. debug 자동 생성 키의 표준 암호는 배포 자격 증명이 아니다.
- 공개 APK는 이전 버전과 같은 RSA3072 인증서 계보를 유지한다. 인증서 SHA256: `5e017fe5163897e92ca83aa9a8b921ade3ccf065b2a6d53071c4369da74b8010`. 이 인증서는 처음에 개발용으로 생성됐으며 상용 신뢰/Play Protect 승인을 의미하지 않는다.
- 소스 신규 빌드의 debug 키는 별도이며 업데이트 호환성이 없다. 새 릴리스 키가 필요하면 서명 전환/사용자 마이그레이션을 별도 설계해야 한다.
- GitHub Actions는 저장소의 이미 서명한 APK를 검증·첨부한다. 표준 저장소용 `github.token`만 해당 작업에 사용하고 파일에 토큰 값을 기록하지 않는다. 외부 서비스나 저장소에 대한 권한은 요청하지 않는다.

## 앱 경계와 개인정보

WebView는 로컬 번들만 표시하고 임의 원격 탐색을 막는다. 파일/콘텐츠 접근, mixed content, DOM storage는 네이티브 앱에서 비활성화했다. 가사는 textContent/생성된 DOM 텍스트로 넣으며 HTML로 실행하지 않는다. 커스터마이징은 허용된 속성/범위만 받는다. 네트워크는 HTTPS와 Android 기본 인증서 검증을 사용한다. 크기·시간 제한과 곡 세대 검사를 적용했다.

곡 제목/가수는 선택한 가사 제공처로 전송한다. 음악 계정 로그인/API 키/쿠키를 요구하지 않는다. 오디오 샘플은 기기에서 분석 후 버리고 녹음 파일/네트워크 업로드를 만들지 않는다. RECORD_AUDIO는 Visualizer/AudioPlaybackCapture용이며 마이크 입력을 열지 않는다. 사용자가 로그를 공유할 때는 개인 곡 정보·알림·기기 식별자를 삭제해야 한다.

EAPI의 고정 암호화 포맷 상수는 공개 프로토콜 재구현 값으로, 사용자 계정 키나 APK 서명 키가 아니다. 토큰 검사 결과에서 이를 개인 자격 증명으로 오해하지 않도록 [API 문서](API_REFERENCE.md)에 구분했다.

## 라이선스와 확인 범위

| 구성 | 확인 | 배포 처리 |
| --- | --- | --- |
| BetterLyrics v1.3.527.0 | 원본 LICENSE는 GNU GPL v3 전문. 원작 Zhe Fang / contributors | GPL 고지, 원본 링크, Android 수정 명시, 대응 소스/빌드 도구 공개 |
| 모바일 구현 | Java/HTML/CSS/JS/Python | 저장소 GPL-3.0, 보증 없음 |
| Bootstrap Icons1.13.1 | MIT 고지 보존 | APK의 licenses/bootstrap-icons-MIT.txt, 제3자 고지 |
| Android SDK/시스템 API·폰트 | 플랫폼 제공, 외부 AAR/JAR 없음 | SDK/JDK/시스템 폰트 재배포 안 함 |
| org.json20250517, Playwright1.62.1 | 테스트 전용; 해당 태그 LICENSE: Public Domain / Apache-2.0 | APK에 포함하지 않음. 해당 태그의 upstream LICENSE 본문 확인 |
| REA / ILSpy / Windows DLL / Lyricify helper | 과거 분석 도구/대상 | APK나 공개 소스 번들에 포함하지 않음. helper 전체 라이선스 검토를 이번에 완료했다고 주장하지 않음 |
| LRCLIB | 공개 API 문서 확인 | 가사 콘텐츠 저작권은 서버 코드 라이선스와 별개 |
| NetEase EAPI | 비공식 형식; 약관 본문 조회 실패 | 사용/배포 권한 검토 미완료. 접근 가능성을 허가로 해석하지 않음 |
| 앨범 아트/가사/상표 | 각 권리자 소유 | 실제 음악 콘텐츠를 APK/테스트 결과에 번들하지 않음; README는 합성 예시 |

원본 배포물과 디컴파일 소스를 공개하지 않으며 정적 분석 내역과 직접 작성한 Android 구현을 구분한다. GPL을 적용했다고 모든 외부 콘텐츠/API의 이용 조건이 해결되는 것은 아니다. NetEase 등의 서비스 사용 허가, 모든 콘텐츠의 배포 권리 확인은 남아 있다. [원본 LICENSE](https://github.com/jayfunc/BetterLyrics/blob/v1.3.527.0/LICENSE), [LRCLIB 문서](https://lrclib.net/docs), [NetEase 약관](https://st.music.163.com/official-terms/service).
