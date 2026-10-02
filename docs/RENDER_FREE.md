# 내 PC를 끄고도 접속하기 — 무료 배포 준비

## 현재 상태

이 문서와 `render.yaml`은 **배포 설정**이다. GitHub에 올리는 것만으로 Render 서비스,
Atlas DB, 계정 또는 실제 URL이 생성되는 것은 아니다. 현재 로컬 터널은 변경하지 않는다.
배포 전에 본인 계정에서 무료 플랜과 결제수단 미등록 상태를 확인해야 한다.

## 비용 조건 — 하나라도 충족되지 않으면 중단

2026-09-30 확인 기준:

- Render: 무료 워크스페이스 + Web Service **Free / $0** 하나, **결제수단 미등록**.
- DB: MongoDB Atlas **Free (M0) / $0**. Flex, Dedicated/M10, 유료 백업은 선택하지 않는다.
- Render 제공 `onrender.com` 주소와 HTTPS만 사용한다. 도메인을 구매하지 않는다.
- 디스크, 워커, Cron, 유료 고정 IP, 유료 미리보기, 자동 확장은 만들지 않는다.
- 앱 AI 기능은 Docker 시작 명령에서도 `--app.openai.enabled=false`로 고정한다.
  OPENAI_API_KEY를 입력하지 않는다. 유료 API 호출을 이 배포에서 활성화하지 않는다.
- 카드 인증, 결제 정보, 유료 요금, 유료 전환이 요구되면 **거기서 중단**한다.
  카드를 등록했다가 나중에 취소하는 방식도 사용하지 않는다.
- 무료 한도에 도달하면 중단을 받아들인다. 결제하거나 무료 제한 회피용 주기적 접속을 만들지 않는다.

Render는 결제수단이 있으면 무료 서비스라도 트래픽/빌드 초과분을 청구할 수 있다.
결제수단이 없으면 한도 초과 시 서비스 또는 신규 빌드가 제한된다.
`plan: free`는 컴퓨팅 플랜이며 워크스페이스 결제 상태나 모든 부가 비용을 통제하지는 않는다.
설정 파일만으로 영구적인 무료 정책이나 무중단 가동을 보장할 수 없다.

## 기대할 수 있는 동작과 제한

| 항목 | 무료 구성 |
|---|---|
| 내 PC 종료 | 배포 완료 후 웹 서버와 DB가 모두 클라우드에 있으면 사용 가능 |
| 접속 주소 | 서비스가 유지되는 동안 같은 `onrender.com` 주소. 실제 주소는 Render가 발급 |
| HTTPS | Render 제공 인증서 사용 |
| 절전 | 15분간 수신 HTTP 요청/기존 WebSocket 메시지가 없으면 Render가 절전 |
| 절전 후 접속 | 자동 기동. Render 안내는 약 1분이며 앱/DB 기동에 따라 달라짐 |
| 가동 시간 | 워크스페이스 전체 월 750시간 공유; 초과 시 다음 달까지 무료 서버 중단 |
| 메모리 | 서버 전체 512 MB. Java 힙은 컨테이너 제한의 50%를 기본 상한으로 설정 |
| DB | Atlas Free는 데이터+인덱스 합계 0.5 GB. 100 operations/sec 등 제한 있음 |
| 신뢰성 | 유료 운영 서비스처럼 항상 즉시 응답하거나 무중단을 보장하지 않음 |
| 알림 | 현재 구현은 연결된 브라우저에서 수신. 닫힌 브라우저 Web Push는 별도 기능 |

Render 무료 파일시스템은 재배포/절전 때 사라진다. 컨테이너 안에 MongoDB를 설치하거나
첨부파일을 영구 저장하지 않는다. 사용자가 추가한 자료와 계정/대화의 백업은 별도로 관리한다.
Atlas도 30일간 연결이 없는 무료 클러스터를 일시 중지할 수 있다.

## 1. MongoDB 확인

이미 사용하는 DB가 Atlas Free라면 새 DB를 만들 필요가 없다.
`mongodb+srv://...mongodb.net/...` 형태는 Atlas 연결의 단서일 뿐, 무료 플랜임을 증명하지 않는다.
Atlas 콘솔에서 **Free/M0, $0**를 확인한다.
DB가 localhost, 127.0.0.1, 172.30.1.8 등 본인 PC에 있으면 그 DB를 그대로 사용할 수 없다.
무료 Atlas 클러스터를 만들거나 기존 클라우드 무료 DB를 사용해야 PC 의존성이 사라진다.

새 Atlas를 만드는 경우 Free를 명시적으로 선택한다. 기본 추천이 Flex/Dedicated이면 변경한다.
새 DB에는 기존 계정/대화가 자동 복사되지 않는다. 데이터 이관은 별도 작업이며 이 설정이
기존 DB를 덮어쓰거나 삭제하지는 않는다. 우선 별도 개발 DB와 테스트 계정을 권장한다.

DB 사용자는 앱의 대상 DB에 필요한 권한만 부여한다. 현재 앱은 시작 시 컬렉션/인덱스를 만든다.
보통 해당 DB의 `readWrite`로 필요한 작업을 할 수 있으나 실제 오류를 확인한다.
Atlas 관리자 로그인 비밀번호가 아닌 **DB 사용자 비밀번호**가 연결 문자열에 들어간다.
비밀번호의 예약 문자는 URI 인코딩하고, DB 이름이 `/messenger` 등으로 명시돼 있어야 한다.
연결 문자열, AES/JWT 키를 채팅·스크린샷·Git에 공개하지 않는다.

## 2. Render에 서비스 설정

추천된 Render 앱을 연결하면 계정 권한 범위에서 작업할 수 있다.
연결 전에는 실제 워크스페이스/결제 상태를 조회하거나 서비스를 생성할 수 없다.
도구로 결제 상태를 확인할 수 없으면 사용자가 대시보드에서 확인하기 전까지 생성하지 않는다.

대시보드에서 직접 진행할 때:

1. Render에 로그인하고 **무료 워크스페이스, 카드 미등록**을 확인한다.
2. `New → Blueprint`에서 `bluewlsghks/messenger` 저장소를 연결한다.
3. 브랜치 **`feat/discord-server-channels`**, Blueprint 경로 **`render.yaml`**을 선택한다.
   기본 `master`에는 아직 변경이 없다. 기존 PR 병합은 별도 작업이다.
4. 생성 항목이 Web Service 하나이고 플랜이 **Free / $0**인지 다시 확인한다.
   이름이 기존 서비스와 겹치면 기존 서비스를 변경하지 말고 새 이름을 사용한다.
5. 요구하는 비밀 환경 변수 세 개를 대시보드에 각각 입력한다.

| Key | 입력할 값 |
|---|---|
| `MONGODB_URI` | 확인된 무료 클라우드 DB의 연결 문자열, DB 이름 포함 |
| `APP_AES_KEY_BASE64` | 기존 암호화 데이터를 사용할 때 반드시 기존 키 유지 |
| `APP_JWT_SECRET_BASE64` | 충분히 긴 기존 키 또는 새 환경용 독립 키. 재배포 때 바꾸지 않음 |

새 DB를 사용하는 완전히 새 환경에서만 새 키를 생성한다. 터널에 사용했던
`SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=8081`, `APP_ALLOWED_ORIGINS`를 Render로 복사하지 않는다.

`New → Web Service`로 수동 생성하는 경우 `render.yaml`은 자동 적용되지 않는다.
그때는 별도로 다음을 정확히 지정해야 한다:

- Repository: `bluewlsghks/messenger`
- Branch: `feat/discord-server-channels`
- Language/runtime: `Docker`, Dockerfile: `./Dockerfile`, context: 저장소 루트
- Region: `Singapore`, Instance type: **Free**, Auto-deploy: **Off**
- Health check path: `/login`, 위의 비밀 변수 3개, `APP_OPENAI_ENABLED=false`
- Disk/유료 부가 기능/커스텀 Docker 실행 명령: 설정하지 않음

## 3. Atlas 네트워크 허용

Render 서비스 상세의 `Connect → Outbound`에 나오는 **모든 송신 IP 범위**를
Atlas 프로젝트의 IP Access List에 추가한다. Render 지역별 공유 송신 범위를 쓰므로
유료 Dedicated Outbound IP는 구매하지 않는다.
`0.0.0.0/0` 전체 허용이나 본인 PC 공인 IP 하나만 추가하는 것으로 대체하지 않는다.
상세 화면을 보려고 서비스를 먼저 생성했다면 초기 빌드/DB 연결이 실패할 수 있다.
송신 범위를 허용한 뒤 수동 Deploy로 재시도한다. 불필요한 반복 빌드는 피한다.

## 4. 도메인/포트 자동 적용

Render는 `PORT`, `RENDER_EXTERNAL_URL`을 제공한다. `scripts/start-render.sh`는
실제 `RENDER_EXTERNAL_URL` 하나만 Origin으로 허용하고 `0.0.0.0:$PORT`로 서버를 실행한다.
로컬 터널의 임시 주소를 매번 IntelliJ에 복사했던 작업은 이 배포에서는 필요 없다.
Render가 외부 HTTPS를 종료하고 내부 HTTP로 전달하며, 브라우저의 기존 상대 URL API와
SockJS 연결을 그대로 사용한다. 모든 출처를 허용하는 `*` 설정은 추가하지 않는다.

시작 스크립트는 Render의 `https://...onrender.com`만 받는다. 커스텀 도메인이 필요해지면
별도 검토가 필요하다. 사용자 제공 Forwarded 헤더를 신뢰하도록 완화하지 않는다.

## 5. 배포 완료 판정

Render `Live` 상태와 실제 발급 주소의 `/login`부터 확인한다.
그다음 서로 다른 두 계정으로 친구 목록, DM/채널 송수신, 새로고침 후 내역을 확인한다.
로컬 IntelliJ 서버/터널을 종료한 뒤 휴대폰 데이터로 같은 주소에 접속해 PC 독립성을 확인한다.
데스크톱 알림 권한도 **새 Render 주소에서** 다시 허용해야 한다.

Health check `/login`은 HTTP 화면 검사이지 지속적인 DB 상태/메시지 전송 검사가 아니다.
DB 접속 실패면 네트워크 허용/사용자 권한/URI/DB 이름을 확인하고 비용을 올려 해결하지 않는다.
메모리 초과가 반복되면 실행을 멈추고 앱을 줄이는 방향으로 점검한다. 유료 플랜으로 전환하지 않는다.

## 검증 범위

`src/test/deployment/test_render_free.py`는 무료 플랜/비밀 값 미포함과 실행 인자를 검사한다.
`Render free deployment checks`는 실제 Docker 이미지를 빌드하고 별도 MongoDB 8 컨테이너,
512 MB 메모리 제한 아래 로그인·허용 Origin·친구·DM 저장/조회를 검사한다.
이 테스트는 무료 플랜의 0.1 CPU 부하 시험이나 실제 Render/Atlas 배포 성공을 뜻하지 않는다.
기존 Messenger CI의 Java/Node/브라우저 검사는 별도로 유지된다.
CI는 표준 공개 저장소 runner와 일회성 로컬 DB만 사용하며 Render/Atlas API를 호출하지 않는다.

## 공식 근거 (확인일: 2026-09-30)

- Render 무료 제한/초과 처리: https://render.com/docs/free
- 무료 인스턴스도 카드 등록 시 초과 과금 가능: https://render.com/docs/faq
- 가격: https://render.com/pricing
- 포트/HTTPS/기본 주소: https://render.com/docs/web-services
- 플랫폼 환경 변수: https://render.com/docs/environment-variables
- Blueprint: https://render.com/docs/blueprint-spec
- 비밀 값/빌드 보안: https://render.com/docs/docker-secrets
- DB 허용 IP: https://render.com/docs/outbound-ip-addresses
- Atlas 가격: https://www.mongodb.com/pricing
- Atlas 무료 제한: https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/
- GitHub 공개 표준 runner 과금: https://docs.github.com/en/billing/concepts/product-billing/github-actions
