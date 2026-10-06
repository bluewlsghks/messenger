> **2026-10-06 후속 변경:** 이 문서는 초기 구현/검증 기록도 포함합니다. 현재 공유 통화 상태·최대 4명 미디어·독립 터널 guardian·Boot 4 설정은 [로드맵 구현](ROADMAP_IMPLEMENTATION.md)과 [운영 안내](OPERATIONS.md)를 우선 참조하세요.

# IntelliJ Run/Debug와 함께 공개 URL 자동 생성

## 한 번만 설정하기

이 기능은 `feature/voice-calls-20261006`에 추가했습니다. 아직 master에 병합되지 않은 경우 해당 브랜치를 체크아웃합니다. 이미 수정 중인 로컬 파일을 강제로 초기화하지 않습니다.

IntelliJ에서 **Run → Edit Configurations → 현재 MessengerApplication 실행 설정 → Environment variables**에 다음 변수를 추가합니다. 항목이 숨겨져 있으면 **Modify options → Environment variables**를 켭니다.

```text
APP_PUBLIC_TUNNEL_ENABLED=true
```

기존 `MONGODB_URI`, `APP_AES_KEY_BASE64`, `APP_JWT_SECRET_BASE64` 값은 그대로 유지합니다. 환경 변수 목록 전체를 위 한 줄로 덮어쓰지 않습니다. 이전처럼 포트 8081을 사용할 때는 `SERVER_PORT=8081`도 유지합니다. 별도로 지정하지 않으면 현재 Spring 설정의 `server.port`를 사용하며 저장소 기본값은 8080입니다.

실행 대상은 `com.individual.messenger.MessengerApplication`, Working directory는 프로젝트 루트(`$PROJECT_DIR$`)로 둡니다. 이후 **Run 또는 Debug 버튼만 누르면 됩니다.** Before launch에 장시간 실행되는 터널 명령을 추가하거나 별도 PowerShell 창을 열 필요가 없습니다. `bootRun` 및 실행 JAR도 이 main을 사용하므로 같은 옵션을 적용할 수 있습니다.

기본값은 `false`입니다. 이 옵션을 설정하지 않은 평소 실행·테스트·배포를 자동으로 외부에 공개하지 않습니다.

## 실행 순서

1. Spring 환경 설정을 읽은 뒤, HTTP 보안·WebSocket Bean 초기화 전에 cloudflared를 실행합니다.
2. `.public/idea-*/quick.yml`에 빈 격리 설정을 만들고 `http2`로 로컬 HTTP 포트에 연결합니다. 사용자의 기존 Cloudflare 설정은 변경하지 않습니다.
3. 새 `https://…trycloudflare.com` Origin을 추출하고 **현재 공개 Origin + 현재 포트의 localhost/127.0.0.1**만 HTTP CORS와 SockJS에 함께 적용합니다. 이전 수동 `APP_ALLOWED_ORIGINS`는 이 실행에서 대체합니다. `*` 또는 전체 `*.trycloudflare.com`을 허용하지 않습니다.
4. 애플리케이션이 시작되면 IntelliJ 콘솔에 `/login` 접속 URL을 다시 출력합니다. `URL ISSUED`는 주소 발급 단계이며, `MESSENGER STARTED`는 애플리케이션 시작 단계입니다. 둘 다 실제 외부 접속 검증을 완료했다는 뜻은 아닙니다.
5. Spring Context 정상 종료, 시작 실패 또는 JVM 정상 종료 시 **이번 실행이 만든 cloudflared 프로세스만** 종료합니다. 다른 Java/터널 프로세스를 일괄 종료하지 않습니다.

콘솔 예시(실제 주소 아님):

```text
[Public tunnel] URL ISSUED (app is still starting): https://example-name.trycloudflare.com
[Public tunnel] MESSENGER STARTED — public login URL: https://example-name.trycloudflare.com/login
```

주소는 해당 실행의 `.public/idea-*/url.txt`, cloudflared 출력은 `tunnel.log`에 저장합니다. `.public/`은 기존 `.gitignore`에 제외되어 있습니다. 프로세스 종료를 감지하면 해당 URL 파일을 지웁니다. 로그는 로컬 진단용으로 남깁니다.

## 옵션 및 오류 처리

| 환경 변수 | 기본값 / 용도 |
|---|---|
| `APP_PUBLIC_TUNNEL_ENABLED` | `false`. 명시적으로 `true`일 때만 자동 공개 |
| `APP_PUBLIC_TUNNEL_EXECUTABLE` | `cloudflared`. PATH 또는 Windows의 일반적인 WinGet 설치 위치 탐색. 직접 설치 경로 지정 가능 |
| `APP_PUBLIC_TUNNEL_TIMEOUT_SECONDS` | `90`. URL 발급 대기 제한, 1~300초 |
| `SERVER_PORT` | 기존 Spring 설정 값 사용. 1~65535의 고정 포트 필요 |

cloudflared가 이미 설치되어 있어야 합니다. 이 기능은 실행 파일을 자동 다운로드하거나 설치하지 않습니다. IntelliJ가 설치 전 PATH를 유지하거나 사용자 지정 위치에 설치했다면 환경 변수 편집기의 이름/값 행에 실제 경로를 추가합니다. 값에 별도의 따옴표를 넣지 않습니다.

```text
이름: APP_PUBLIC_TUNNEL_EXECUTABLE
값: C:\실제 설치 폴더\cloudflared.exe
```

명령은 셸 문자열이 아닌 `ProcessBuilder`의 분리된 인자로 전달하므로 경로에 공백이 있어도 처리합니다. 설치 경로 예시는 실제 경로로 바꿉니다.

포트가 이미 점유되어 있으면 다른 서버를 공개하지 않도록 터널 시작을 거부합니다. 실행 파일 없음, 발급 시간 초과, 시작 중 종료 시 오류를 표시하고 소유한 프로세스를 정리합니다. 로컬만 실행하려면 `APP_PUBLIC_TUNNEL_ENABLED=false`로 바꿉니다. 실행 중 터널이 끊기면 콘솔에 안내하며 새 주소를 조용히 재발급하지 않습니다. 앱을 재실행해야 합니다. 기존 터미널에서 직접 띄운 터널은 이 기능이 소유하지 않으므로 해당 창에서 먼저 종료합니다.

자동 공개 실행에서는 로컬 바인딩을 `127.0.0.1`로 제한하고 전달 헤더 신뢰를 끄며, 의도하지 않은 외부 AI 사용을 줄이기 위해 `app.openai.enabled=false`를 적용합니다. MongoDB/AES/JWT 비밀값은 바꾸거나 출력하지 않습니다. 로컬 HTTPS 서버(`server.ssl.enabled=true`)와 랜덤 포트(`server.port=0`)는 이 실행 모드에서 지원하지 않습니다.

## 범위 및 주의

- 계정·도메인 없이 사용하는 개발용 Quick Tunnel입니다. 주소는 재실행 시 변경되며 PC·메신저·터널 중 하나라도 중지되면 서비스 접속이 유지되지 않습니다. 상시 무료 서버 배포나 고정 도메인을 만드는 기능이 아닙니다.
- URL을 아는 사람은 로그인/회원가입에 접근할 수 있고 HTTP 트래픽은 Cloudflare를 통과합니다. 개발 DB와 테스트 계정을 사용합니다. 가입/로그인 요청 제한 등 운영 보안 과제는 별도입니다.
- HTTPS 웹 터널은 **WebRTC 음성용 TURN 서버가 아닙니다.** 외부망 음성 연결은 별도 STUN/TURN 조건에 영향을 받습니다.
- 정상 종료 훅을 사용합니다. OS의 강제 종료, JVM 크래시, IntelliJ의 강제 Kill까지 자식 프로세스 정리를 보장하지 않습니다. 필요 시 이번 터널의 프로세스만 확인하여 종료합니다.

## 검증

2026-10-06 Java 21/Linux에서 `QuickTunnelProcess`와 가짜 Java 자식 프로세스로 **오프라인 17개 검사 통과**를 확인했습니다. URL 추출, 격리 설정, 파일 저장/삭제, 정상·중복 종료, 시간 초과, 조기/실행 중 종료, 위장 호스트·HTTP 거부, 점유/랜덤 포트, 누락된 실행 파일, 시작 중 인터럽트를 검사합니다. 실제 cloudflared 실행이나 인터넷 공개는 하지 않았습니다.

```text
javac -encoding UTF-8 -d build/tunnel-smoke src/main/java/com/individual/messenger/dev/QuickTunnelProcess.java tests/java/QuickTunnelSmoke.java
java -cp build/tunnel-smoke com.individual.messenger.dev.QuickTunnelSmoke
```

`PublicTunnelListenerTest`에 Spring 설정·수명주기 테스트 10개를 추가했습니다. 기존 Gradle 테스트와 별도로 Windows/Linux 오프라인 프로세스 CI를 추가했습니다. **이 문서의 로컬 17개 통과는 전체 Spring 빌드/10개 테스트 통과, 실제 Windows IntelliJ 실행, Cloudflare 외부 접속, 실제 음성통화 검증을 의미하지 않습니다.** 해당 결과는 PR의 CI와 별도 기기 검증으로 확인합니다.

참고: [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/), [IntelliJ Application 실행 설정](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html).
