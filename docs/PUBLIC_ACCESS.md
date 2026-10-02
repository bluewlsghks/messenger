# 무료 HTTPS 외부 접속 (Windows 개발용)

현재 PC의 Spring Boot 메신저를 Cloudflare **Quick Tunnel**의 임시 서브도메인으로 공개합니다.
도메인을 소유/구매/등록하는 기능이 아닙니다. 계정·카드·공유기 포트포워딩은 필요하지 않습니다.
이 문서를 저장소에 추가하는 것만으로 실제 주소가 발급되거나 PC가 공개되는 것은 아닙니다.
**메신저가 설치된 본인 Windows PC에서 아래 명령을 실행해야 합니다.**

```text
다른 사람의 브라우저 → https://임의이름.trycloudflare.com
                    → Cloudflare → PC의 cloudflared → 127.0.0.1:8081
```

## 1. 업데이트 및 cloudflared 설치 (최초 1회)

로컬 수정은 먼저 커밋/보관합니다. 이 변경은 master가 아니라 개발 브랜치에 있습니다.

```powershell
git fetch origin
git switch feat/discord-server-channels
git pull --ff-only origin feat/discord-server-channels
winget install --id Cloudflare.cloudflared --exact
```

로컬 브랜치가 없으면 `git switch --track origin/feat/discord-server-channels`를 사용합니다.
설치 후 PowerShell/IntelliJ 터미널을 다시 열고 `cloudflared --version`을 확인합니다.
winget을 사용할 수 없으면 Cloudflare 공식 다운로드 페이지의 Windows 실행 파일을 받고
`-CloudflaredPath 'C:\경로\cloudflared.exe'`로 지정합니다. 관리자 권한이나 방화벽 전체 해제는 필요 없습니다.

## 2-A. IntelliJ의 기존 실행 설정을 그대로 사용하는 방법

DB/키가 IntelliJ Run Configuration에만 있으면 이 방법이 가장 간단합니다.
저장소 루트에서 아래 명령을 실행하고 터미널을 유지합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Public.ps1 -TunnelOnly
```

임시 HTTPS 주소와 IntelliJ에 넣을 환경 변수 한 줄이 출력됩니다.
IntelliJ → Run → Edit Configurations → 현재 Spring Boot 실행 항목 → Environment variables에
출력된 `SERVER_PORT`, `SERVER_ADDRESS`, `APP_ALLOWED_ORIGINS`,
`SERVER_FORWARD_HEADERS_STRATEGY`, `APP_OPENAI_ENABLED`를 추가/교체하고 서버를 재시작합니다.
기존 MongoDB/AES/JWT 값은 유지합니다. 같은 이름의 환경 변수를 중복으로 추가하지 마세요.
Program arguments에 같은 속성이 이미 있으면 환경 변수보다 우선하므로 그 값도 일치시킵니다.

스크립트는 로컬/외부 `/login` 및 `/ws-stomp/info`를 실제 GET으로 확인합니다.
`HTTPS LOGIN + SOCKJS ORIGIN CHECK PASSED`가 출력되면 HTTPS 주소를 공유합니다.
이 검사는 로그인 화면·SockJS 출처 확인이며, 실제 JWT 로그인·메시지 전달까지 확인한 것은 아닙니다.
접속자는 프로그램 설치 없이 브라우저만 쓰면 됩니다. 주소 뒤에 `:8081`을 붙이지 않습니다.

## 2-B. 서버와 터널을 한 명령으로 실행하는 방법

IntelliJ의 기존 서버를 먼저 중지합니다. Java 21이 PATH에 있어야 합니다.
아래 파일은 최초 한 번만 복사한 후 로컬 편집기로 채웁니다. 이미 만든 파일을 덮어쓰지 마세요.

```powershell
Copy-Item .env.public.example .env.public
notepad .env.public
```

세 항목에 **기존** 환경 변수 값을 그대로 넣습니다. 쉘 환경 변수가 이미 있으면 그 값이 우선합니다.
공개 테스트용 DB를 권장하며 기존 암호화 데이터의 AES 키는 바꾸지 않습니다.
이 파일은 실행하지 않고 문자열만 읽습니다. `$env:...` 등 변수 치환은 하지 않습니다.
키·DB 비밀번호를 GitHub나 채팅에 올리지 마세요. `.env.public`과 `.public/`은 Git에서 제외합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Public.ps1
```

빌드 → 임시 주소 발급 → 발급된 주소만 허용하는 앱 실행 → 접속 확인 순서로 동작합니다.
기본 포트는 8081이며 `-Port 8082`로 변경할 수 있습니다.
소스 변경 없이 만들어 둔 JAR을 다시 실행할 때만 `-SkipBuild`를 사용합니다.
창을 유지하다가 종료하려면 Ctrl+C를 누릅니다. 스크립트가 시작한 프로세스만 종료하며,
`-TunnelOnly`로 실행한 경우 IntelliJ 서버를 종료하지 않습니다. 터미널 강제 종료 후에는
작업 관리자에서 남은 cloudflared/Java 프로세스를 확인하세요.

## 보안 및 동작 범위

- 공개 URL은 접근 통제 수단이 아닙니다. 링크를 아는 사람은 로그인·회원가입 화면에 접근합니다.
  앱의 기존 JWT/채팅방 권한 검사는 유지하지만 가입 승인, 요청 제한, 운영 보안 검토가 완성된 것은 아닙니다.
  업무/민감 데이터 대신 테스트 계정을 사용하세요.
- 명령 기본 실행은 서버를 `127.0.0.1`에만 바인딩합니다. 따라서 `172.30.1.8:8081` 직접 접속은
  되지 않는 것이 정상입니다. 공유한 HTTPS 주소나 서버 PC의 localhost로 접속합니다.
- 기존 공유기 포트포워딩/Windows 방화벽 규칙은 만들거나 지우지 않습니다. 이 방식에 필요하지 않습니다.
  회사 등 관리망에서는 외부 공개 권한과 네트워크 정책을 확인하세요.
- `APP_ALLOWED_ORIGINS`에는 정확히 발급된 한 주소와 localhost 주소만 넣습니다.
  `*` 또는 `https://*.trycloudflare.com` 전체 허용으로 바꾸지 마세요.
- 외부 HTTPS는 Cloudflare에서 종료되고 로컬에는 HTTP가 전달됩니다. 앱은 명시적 출처 목록을
  사용하며 전달 헤더를 신뢰하지 않습니다. 기존 상대 경로 API/SockJS는 그대로 동작합니다.
  SSL 개인 키나 인증서 발급 작업은 필요하지 않습니다. Cloudflare가 HTTPS 중계자입니다.
- 공개 실행에서는 유료 외부 AI 호출을 꺼 둡니다. 환경 변수 파일에 API 키를 보관할 필요가 없습니다.
- HTTPS 주소에서 메신저 설정 → 데스크톱 알림 허용을 다시 선택합니다. 주소가 바뀌면
  로그인·알림 권한·테마·초안 같은 브라우저의 출처별 상태도 이전 주소와 별개입니다.
- 메신저 탭/브라우저를 닫은 상태의 백그라운드 Web Push는 추가되지 않았습니다.
- Quick Tunnel은 재실행 시 주소가 바뀌고 PC 절전/종료, 앱/터널 중지 시 접속이 끊깁니다.
  가동 시간 보장 없는 개발용이며 최대 200 동시 진행 요청, SSE 미지원 제한이 있습니다.
  현재 통합 화면은 STOMP/SockJS를 사용합니다. 옛 `/api/sse` 경로의 스트리밍은 지원하지 않습니다.

## 오류 확인

`Port 8081 is already occupied`: 기존 서버를 중지하거나 `-TunnelOnly`로 실행합니다.
`Missing ...`: 일반 실행은 IntelliJ 환경 변수를 자동으로 읽지 못합니다. `.env.public`을 채우거나 2-A를 사용합니다.
`cloudflared` 없음: 설치 후 새 터미널에서 실행하거나 실행 파일 경로를 지정합니다.
주소 발급 실패: Cloudflare HTTPS 443 및 터널용 TCP 7844 외부 통신이 허용되는지 확인합니다.
502/접속 확인 실패: `.public/<실행ID>/app-out.log`, `app-err.log`, `tunnel-err.log`를 로컬에서 확인합니다.
화면은 열리지만 연결 거부: 현재 발급 주소와 허용 출처가 일치하는지, 변경 후 앱을 재시작했는지 확인합니다.
로그에는 연결 정보가 포함될 수 있으므로 파일 전체를 공개 업로드하지 마세요.

## 고정 무료 주소가 필요할 때

ngrok Free는 계정에 할당되는 개발용 도메인을 제공합니다. 본인 계정 생성·인증 토큰 설정이
필요하며 할당 이름을 자유롭게 정하는 개인 소유 도메인은 아닙니다. 무료 사용량/안내 페이지
제한이 있으므로 지속 운영용 무제한 호스팅이라고 볼 수 없습니다. 이 PR은 ngrok 계정이나
고정 주소를 생성하지 않았습니다. Cloudflare의 고정 사용자 도메인 방식도 별도 도메인이 필요합니다.

## 검증

CI에서 Windows PowerShell 5.1/PowerShell 7 구문·파싱·환경 파일·포트 검사를 수행합니다.
Java 테스트는 실제 Spring Boot/MongoDB에서 HTTPS Origin → 로컬 HTTP 허용,
다른 출처/위조 전달 헤더 거부, WebSocket 핸드셰이크, 인증이 여전히 필요한지를 검증합니다.
CI에서는 실제 Cloudflare 터널을 만들거나 사용자의 PC/DB를 공개하지 않습니다.
따라서 실제 도메인 발급 및 사용자 회선에서의 외부 연결은 본인 PC에서 실행해야 검증됩니다.

공식 문서 (확인: 2026-09-30):
- https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/
- https://developers.cloudflare.com/tunnel/downloads/
- https://developers.cloudflare.com/network/websockets/
- https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/tunnel-with-firewall/
- https://ngrok.com/docs/pricing-limits/free-plan-limits
