# 💻 이진환 | Backend Developer

> **복잡한 업무를 이해하고, 데이터가 정확하게 흐르는 백엔드를 만듭니다.**

**7+ years · Java / Spring · ERP / MES · Real-time Systems**  
📧 [bluewlsghks@gmail.com](mailto:bluewlsghks@gmail.com) · 🔗 [Notion 이력서](https://chipped-falcon-031.notion.site/Backend-Developer-7-years-3ed42116bdcf80408ee7ffb8d62d9e7c?source=copy_link)

## 👨‍💻 Developer Profile

ERP 운영에서 쌓은 문제 해결력에, MES·실시간 서비스 설계 경험을 더한 Java/Spring 백엔드 개발자입니다. 인사·근태·복무·전자결재 연계 ERP에서 복잡한 업무 규칙, SQL 성능 병목과 운영 장애를 다뤘으며, 현재는 MES/PMS 생산관리 백엔드와 설비·외부 시스템 연계를 개발하고 있습니다.

개인 프로젝트 **Messenger**에서는 DM·그룹 채팅을 서버·채널형 커뮤니티로 확장하며 접근 권한, 동시 수정 충돌, 조회 경계, 회귀 테스트까지 구현했습니다. WebRTC 기반 1:1 음성통화에는 수락 기반 마이크 접근, 사용자·탭별 통화 제어와 연결 자원 정리를 추가했습니다. 아래에는 개발자 소개와 함께 **실제로 구현한 기능, 근거 코드, 검증 범위, 남은 과제**를 정리했습니다.

### 💼 Career Snapshot

| 기간 | 소속 / 역할 | 주요 경험 |
|---|---|---|
| 2026.03–현재 | ㈜버텍스아이디 · Backend Developer | 농심 녹산 MES/PMS, 작업지시·LOT·실적·리포트, MQTT 수집·큐·배치 저장, ERP 연계, PMS–AMR 통신 설계·협의 |
| 2020.07–2026.03 | ㈜씨앤에프시스템 · Backend Developer / 과장 | 인사·근태·복무·평가 ERP, 전자결재 API, SQL 개선, 리포트, Jenkins 배포·운영 장애 대응 |
| 2020.01–2020.03 | Freelance Developer | 서울바이오허브 시큐어코딩·유지보수, 베개 높이 측정 시스템 기능·UI 설계 |
| 2018.11–2020.01 | ㈜다루소프트 · Backend Developer | 공공기관 웹 개발, 보안 취약점·접근성 개선, LG Mobile Repair 참여 |
| 2018.06–2018.09 | ㈜에이치아이엘 · Backend Developer | IoT 스마트 문 손잡이 서버, Spring Boot API·DB 설계, GitLab 협업 |

### 🧰 Experience Focus

- **업무 시스템:** Java, Spring, Spring Boot, JPA, QueryDSL, MyBatis, eGovFrame; Oracle·MariaDB·MSSQL·Tibero와 Linked Server 기반 데이터 연계.
- **생산·설비 데이터:** MQTT/Mosquitto, 수신·큐·배치 저장, 작업지시 상태 전이와 공정별 LOT·실적 처리. AMR은 통신 방식·토픽 설계 및 협의 경험입니다.
- **개인 실시간 서비스:** MongoDB, REST/STOMP 공통 인가, 복합 커서, 조건부 원자적 갱신, WebRTC 1:1 음성통화, GitHub Actions·브라우저 회귀 테스트.

회사 프로젝트의 기술과 이 저장소의 기술은 구분합니다. **Messenger는 MongoDB·Thymeleaf·Vanilla JavaScript 기반**이며, JPA·Vue 기반 프로젝트가 아닙니다.

### 🛠️ How I Work

**코드가 동작하는 이유만큼, 데이터가 맞는 이유를 설명합니다.** 입력부터 저장·집계·외부 연계까지 앞뒤 업무를 확인하고, 오류를 고칠 때는 재현 조건과 영향 범위를 함께 살핍니다. 구조를 선택한 이유와 예외 상황, 검증 방법을 설명할 수 있는 개발을 지향합니다.

---

# 📬 Messenger · 실시간 메신저

**Java 21 · Spring Boot 4.1.1 · MongoDB · Thymeleaf / Vanilla JavaScript** 기반 개인 프로젝트입니다. DM·그룹·서버/채널 채팅과 1:1 음성통화에, README 후속 과제의 신뢰성·권한·미디어·운영 기능을 추가했습니다.

> 문서 기준: 2026-10-06, `4ffa27f` 이후 로드맵 구현. 기능 브랜치의 코드이며 **master 병합·실서비스 배포·실기기 검증 완료를 뜻하지 않습니다.** 무료 개발 기능과 별도 인프라 준비가 필요한 기능을 구분합니다. 기존 개발자 소개/이력서는 위에 유지했습니다.

[기능](#features) · [구조](#architecture) · [실행](#getting-started) · [과제 처리 현황](#roadmap-status) · [검증](#tests--verification) · [운영 안내](docs/OPERATIONS.md)

## Resume Summary

Java/Spring 백엔드와 MongoDB 기반 실시간 메신저를 설계하고, REST/STOMP 공통 권한 검사·메시지 변경 충돌·복합 커서·읽음·알림과 WebRTC를 구현했습니다. 후속 작업에서는 **클라이언트 요청 ID 멱등 저장, 메시지 문서 내 원자적 outbox, 승인 기반 친구 관계, 채널 권한, 세션 회전/폐기, 다인 WebRTC mesh 및 선택적 분산 브로커·검색 투영**을 추가했습니다. 구현·자동 테스트·운영 검증을 구분하며 수치 근거가 없는 처리량이나 배포 실적을 주장하지 않습니다.

## Tech Stack

| 영역 | 사용 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC/Security/Validation, Gradle Kotlin DSL |
| 저장소 | MongoDB, Spring Data/MongoTemplate, GridFS, 부분 고유·TTL·복합 인덱스 |
| 실시간 | SockJS/STOMP, 기본 Simple Broker, 선택적 RabbitMQ STOMP relay |
| 미디어 | 브라우저 WebRTC; 1:1 DM 및 최대 4명 mesh, STUN/선택적 TURN |
| 인증/보안 | BCrypt, JWT, Mongo refresh 세션, AES-GCM, 별도 운영자 인증 |
| UI | Thymeleaf, Vanilla JavaScript, HTML/CSS, 다크/라이트·반응형·가상 메시지 창 |
| 선택 기능 | Web Push/VAPID/Service Worker, Elasticsearch 비동기 projection, OpenAI Responses API |
| 검증/운영 | JUnit, Mockito, 실제 MongoDB/HTTP, Node test, Playwright, Actuator, OSV, Docker Compose |

버전은 [build.gradle.kts](build.gradle.kts)와 실제 resolved graph를 기준으로 합니다. Java 애플리케이션이며 Vue/JPA 프로젝트가 아닙니다. **Boot 4에서도 기존 JSON 형식을 유지하도록 Jackson 2 호환 모듈을 명시**했습니다. 호환 모듈은 추후 Jackson 3 전환을 대신한 영구 설계가 아닙니다. STOMP/SockJS는 WebJar로 제공하며 npm 프런트 빌드는 필요하지 않습니다.

## Features

| 기능 | 현재 구현 |
|---|---|
| 회원·로그인 | 아이디·표시 이름·비밀번호 가입, 전화번호 입력 주석/전송·필수 검증 제외, BCrypt, 입력 정책, 중복 ID 거부 |
| 세션 | 15분 JWT, HttpOnly refresh cookie, 7일 절대 수명·회전·재사용 감지, 로그인 세션 조회/폐기, 로그아웃 |
| 친구·DM·그룹 | 친구 요청/수락/거절/취소, 차단/해제, 기존 친구 보존, DM 재사용, 그룹 생성 |
| 서버·채널 | 생성/참여, 소유자·관리자 역할, 채널별 읽기/쓰기, 탈퇴·강퇴·재참여 차단, 소유권 이전, 서버 소프트 삭제, 초대 일괄 철회 |
| 메시지 | 저장/실시간 수신, 본인 수정·소프트 삭제, 버전 충돌, 검색, 복합 커서, 대화별 초안·IME·실패 입력 보존 |
| 전달 신뢰성 | 요청 UUID 기반 중복 저장 방지, 메시지 원자적 outbox, 재시도/버전 ACK, 전체 내역 페이지 대조 |
| 대화 확장 | GridFS 파일 전송, 답장·스레드·현재 멤버 멘션, 가상 메시지 DOM 창 |
| 읽음·상태 | readBy 공통 모델, DB unread 집계, 탭별 온라인 lease·입력 중 표시 |
| 알림 | 앱 안/데스크톱/선택적 소리·미리보기·대화 음소거, 선택적 탭 종료 후 Web Push 및 수신 통화 알림 |
| 미디어 | DM 1:1 음성, 그룹/채널 최대 4명 음성·영상·화면 공유, 음소거·장치 선택·통화 이력·벨소리·제한된 ICE restart |
| 선택 인프라 | RabbitMQ 다중 인스턴스 relay, Mongo 공유 통화 상태, Elasticsearch 비동기 색인/재색인 |
| 운영 | 최소 health·보호된 metrics/읽기 전용 감사, 새 DB만 허용하는 복구 도구, 의존성 검사·로컬 재연결 soak |
| 개발 공개 URL | IntelliJ Run/Debug와 선택적 Quick Tunnel 동시 시작, 정확한 Origin 적용, 강제 앱 종료 감시 guardian |

Web Push·브로커·Elasticsearch·TURN은 **설정과 해당 서버/제공자 연결이 필요**합니다. 기본 실행에서 외부 유료 서비스나 Docker 서버를 자동 생성하지 않습니다. OpenAI는 기본 비활성이고 활성화하면 대화 문맥이 외부 API로 전달되므로 참여자 안내와 비용 정책이 필요합니다.

## Architecture

```text
Thymeleaf / JavaScript 작업 화면
  ├─ 인증된 REST / STOMP 명령
  │       ↓
  │   Controller → Service (사용자·멤버십·역할·요청 ID 검사)
  │       ↓
  │   Repository / MongoTemplate → MongoDB
  │        메시지 본문 + pending outbox를 같은 문서에 원자적 저장
  │                         ↓
  │              임대·버전 조건부 outbox worker
  │                 ├─ STOMP 브로커 → 수신 시 재인가 → UI 병합
  │                 ├─ 선택적 Web Push 작업 큐
  │                 └─ 선택적 Elasticsearch projection
  ├─ 재연결 → /sync 페이지를 끝까지 대조
  └─ 통화 → Mongo CAS 공유 제어 상태 / SDP·ICE 단기 암호화 큐
              실제 음성·영상: 브라우저 ↔ WebRTC ↔ 브라우저 (필요 시 TURN)
```

STOMP에 음성 바이트를 보내지 않습니다. 메시지 outbox는 **별도 DB 이중 쓰기가 아니라 메시지 문서 내부 플래그**이므로 독립형 Mongo에서도 동작합니다. 전달은 at-least-once이며 브라우저 ID/버전 병합을 사용합니다. 모든 브라우저의 ACK나 exactly-once 전달을 보장하지 않습니다.

리팩터링한 `controller → service → repository`, 독립 DTO, 공통 security 구분을 유지합니다. 기존 domain 클래스 경로·컬렉션을 보존하여 `_class` 데이터 재작성을 요구하지 않습니다. 초기 구조 정리/삭제 근거는 [PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md), 이번 기능 상세는 [ROADMAP_IMPLEMENTATION.md](docs/ROADMAP_IMPLEMENTATION.md)에 있습니다.

### 핵심 근거 코드

| 주제 | 코드 |
|---|---|
| 멱등 저장 / outbox | [MessageService](src/main/java/com/individual/messenger/service/MessageService.java), [MessageOutbox](src/main/java/com/individual/messenger/service/MessageOutbox.java) |
| 공통 인가 | [ChatAccessService](src/main/java/com/individual/messenger/security/ChatAccessService.java), [StompSecurityInterceptor](src/main/java/com/individual/messenger/security/StompSecurityInterceptor.java) |
| 승인 / 채널 권한 | [ContactService](src/main/java/com/individual/messenger/service/ContactService.java), [ServerAdministration](src/main/java/com/individual/messenger/service/ServerAdministration.java) |
| 파일 / 세션 | [AttachmentService](src/main/java/com/individual/messenger/service/AttachmentService.java), [SessionService](src/main/java/com/individual/messenger/service/SessionService.java) |
| 미디어 공유 상태 | [MediaRegistry](src/main/java/com/individual/messenger/service/MediaRegistry.java), [ConferenceService](src/main/java/com/individual/messenger/service/ConferenceService.java) |
| Push / 검색 | [WebPushService](src/main/java/com/individual/messenger/service/WebPushService.java), [ElasticSearchService](src/main/java/com/individual/messenger/service/ElasticSearchService.java) |
| 공개 URL 자원 정리 | [QuickTunnelProcess](src/main/java/com/individual/messenger/dev/QuickTunnelProcess.java), [TunnelGuardian](src/main/java/com/individual/messenger/dev/TunnelGuardian.java) |

## Getting Started

기존 소스에서 로컬 수정사항을 먼저 커밋/보관한 뒤 기능 브랜치를 갱신하고 Gradle 동기화합니다. **main 클래스는 `com.individual.messenger.MessengerApplication` 그대로**이며 IntelliJ Run/Debug를 사용합니다. `master` 병합 여부는 PR을 확인합니다. 강제 reset으로 로컬 수정사항을 버리지 않습니다.

### 기본 환경 변수

| 변수 | 설명 |
|---|---|
| `MONGODB_URI` | 필수, 개발 DB. 기존 변수 이름 유지; Boot 4 직접 설정 이름은 `spring.mongodb.uri` |
| `APP_AES_KEY_BASE64` | 필수, 기존 AES-GCM 키 유지. 16/24/32바이트의 Base64; 새 환경은 32바이트 사용 |
| `APP_JWT_SECRET_BASE64` | 필수, AES와 다른 32바이트 이상 JWT 키의 Base64 |
| `SERVER_PORT` | 기본 8080, 기존 8081 설정도 유지 가능 |
| `APP_ALLOWED_ORIGINS` | 정확한 공개/로컬 Origin 목록. 기본 localhost/127.0.0.1:8080 |
| `APP_PUBLIC_TUNNEL_ENABLED` | 기본 false. true이면 설치된 cloudflared와 함께 시작·자동 Origin 적용 |
| `APP_PUBLIC_TUNNEL_EXECUTABLE` | 선택, cloudflared 실행 파일 경로 |
| `APP_PUBLIC_TUNNEL_TIMEOUT_SECONDS` | 기본 90, 발급 제한 1~300초 |
| `APP_OPENAI_ENABLED` / `OPENAI_API_KEY` | AI는 기본 false. 공개 터널 모드에서는 false 강제 적용 |
| `VOICE_STUN_URLS` | 기본 공개 STUN. 테스트에서 빈 값이면 외부 STUN을 사용하지 않음 |

```powershell
# 기존 비밀값은 IntelliJ 환경변수/로컬 비밀 저장소에 유지합니다.
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

Linux/macOS는 `bash ./gradlew test bootJar`, `bash ./gradlew bootRun`을 사용합니다. `/login`에서 가입/로그인하고 DM 또는 그룹/채널을 엽니다. 두 계정 테스트는 다른 브라우저 프로필/시크릿 창을 쓰고, 음성에는 HTTPS 또는 localhost와 마이크 권한이 필요합니다.

### 선택 설정

| 기능 | 주요 변수 / 준비 |
|---|---|
| Web Push | `APP_PUSH_ENABLED`, `APP_PUSH_PUBLIC_KEY`, `APP_PUSH_PRIVATE_KEY`, `APP_PUSH_SUBJECT`; 사용자 동의 필요 |
| TURN | `VOICE_TURN_URLS`, `VOICE_TURN_SECRET`, `VOICE_RELAY_ONLY`; 별도 TURN 호스트 필요 |
| RabbitMQ | `APP_BROKER_RELAY_ENABLED`, `…HOST`, `…PORT`, `…LOGIN`, `…PASSWORD`, `…VIRTUAL_HOST` |
| Elasticsearch | `APP_SEARCH_ENABLED`, `APP_SEARCH_URL`, `APP_SEARCH_INDEX`, `APP_SEARCH_API_KEY` |
| 운영 진단 | `APP_OPERATIONS_TOKEN`; 32바이트 이상 별도 키, 기본 미설정이면 접근 거부 |

설치/로컬 Compose/VAPID 생성/백업·복구/실기기 검사 절차는 [OPERATIONS.md](docs/OPERATIONS.md)를 사용합니다. Quick Tunnel이 HTTPS 주소를 제공하더라도 TURN 중계나 상시 서버 배포가 되는 것은 아닙니다.

## API Overview

데이터 API는 Bearer JWT와 현재 자원 권한이 필요합니다. refresh/logout만 HttpOnly cookie·별도 CSRF 방어를 사용합니다. 변경 전 경로는 가능한 한 유지하며 SSE/해시 실험 코드는 이전 정리에서 제거했습니다.

| 영역 | 주요 경로 |
|---|---|
| 인증 | `POST /api/auth/register`, `/login`, `/refresh`, `/logout`; `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{id}` |
| 친구 | 기존 `/api/friends`; `/api/contacts/requests`, `/requests/{peer}`, `/blocks/{peer}` |
| 방 / 서버 | `/api/rooms/dm`, `/group`, `/api/servers`, `/join`, `/{id}/channels`, `/{id}/invites`, `/{id}/administration`, `/{id}/channels/{channel}/permissions` |
| 메시지 | 기존 저장/커서/읽음/수정/삭제/검색 + `GET /api/messages/{room}/sync`, `/{room}/threads/{root}` |
| 첨부 | `/api/files` 업로드, `/{id}` 메타/전송 전 삭제, `/{id}/content` 다운로드 (상세는 controller) |
| 상태 | `/api/presence`, `/api/presence/rooms/{room}`, `/api/presence/contacts` |
| 미디어 | 기존 `/api/voice/config`, `/calls`, `/calls/{id}` + `/current`, `/history`; `/api/conferences/{room}` 참여/조회/제어 |
| Push | `/api/push/config`, `/subscriptions`, `/preferences` |
| 운영 | `/actuator/health`, 보호된 `/actuator/metrics`, `/api/operations/audit`, `/search`, `/search/reindex` |

일반 전송은 `roomId`, `content`, `clientRequestId`(UUID), 선택적 `replyToId`, `attachmentIds`를 받습니다. 발신자 ID/이름을 신뢰하지 않습니다. 파일이 있어도 메시지 본문은 1~4,000자입니다.

STOMP 연결은 `/ws-stomp`, CONNECT에도 JWT가 필요합니다. SEND `/pub/chat.send`·`/pub/ai.ask`, SUBSCRIBE `/user/queue/events`·`/user/queue/errors`·`/topic/chat/{room}`·`/topic/chat/{room}/read`를 사용합니다. 기존 `/sub/chat`은 내부에서 `/topic/chat`으로 변환됩니다. 개인 이벤트에는 MESSAGE_*·VOICE_CALL·CONFERENCE가 포함됩니다.

## Data & Security Notes

기존 `users`, `friends`, `rooms`, `chat_servers`, `server_invites`, `messages`, 호환 `read_cursors`를 보존합니다. 추가 상태는 `auth_sessions`, `contact_relations`, `presence_leases`, `request_budgets`, GridFS/`upload_quotas`, `push_subscriptions`/`push_jobs`, `media_state`/`voice_history`, `search_reindex`입니다. 실제 데이터 삭제/마이그레이션을 실행한 것은 아닙니다.

**전화번호를 신규 수집하지 않지만 기존 암호화 개인정보는 보존합니다.** 비밀값은 Git/로그에 넣지 않습니다. 음성·영상 바이트는 저장하지 않고, 공유 통화 제어를 위해 메타데이터/짧은 SDP·ICE 큐를 서버 AES-GCM으로 임시 암호화 저장합니다. 메타데이터 이력은 본인 통화만 30일 조회합니다. 이는 사용자 신원 기반 E2EE 서비스가 아닙니다.

ID 고유 인덱스 충돌이 있으면 안전하게 시작을 거부합니다. 자동 중복 사용자 삭제는 하지 않습니다. 첨부파일은 인증된 다운로드·형식 제한을 적용하지만 악성코드 검사까지 대체하지 않습니다. 단기 테스트를 운영 보안 감사나 무중단·무손실 전달 보장으로 표현하지 않습니다.

## Roadmap Status

첨부된 기존 과제 목록을 기준으로 처리 상태를 나눴습니다. **코드를 추가한 것, 설정해서 사용하는 것, 실제 운영환경 검증은 서로 다릅니다.**

| 기존 영역 | 이번 구현 | 별도 준비 / 남아 있는 검증 |
|---|---|---|
| 메시지 전달 신뢰성 | UUID 멱등 저장, 원자적 pending outbox/재발행, 전체 페이지 대조, 읽음 통합 | 브라우저 ACK/정확히 한 번 전달 아님; 전체 편집 이벤트 이력은 미보관 |
| 알림·상태·커뮤니티 | Web Push 코드, 온라인/입력 중, 친구 승인·차단, 역할·채널 ACL·탈퇴/강퇴/이전/삭제·초대 철회 | VAPID 설정·실제 브라우저 Push 제공자/OS 도착 확인 |
| 대화 확장 | 파일·답장·스레드·멘션, 최대 4명 음성·영상·화면 공유, 가상 메시지 창 | SFU/대규모 음성방 아님; 실제 화면 선택 UX·대용량 기기별 성능 확인 |
| 음성통화 운영 | 장치 선택·이력·벨소리·Push 수신 안내·ICE restart·DB 공유 통화 제어 | 실제 장치/회사망/모바일망/TURN 중계 확인, 완전히 종료된 앱의 자동 음성 수신은 미지원 |
| 인증·데이터 | refresh 회전/폐기, DB 요청 예산, 입력 정책, 중복/레거시 감사, 단일 읽음 기준 | 운영 데이터 충돌 정비는 백업 후 별도 승인·수행, 계정 복구/전체 보안 감사 별도 |
| 운영·확장 | RabbitMQ relay, 보호된 모니터링, 안전한 새 DB 복구, OSV 검사, 로컬 soak | 운영 HA·백업 보관/복구 훈련, 장시간/대규모 부하·배포망 검증 |
| 개발 공개 터널 | 별도 guardian으로 앱 강제 종료 후 소유 터널 정리 | 실제 Windows IntelliJ/Cloudflare 확인; 고정 주소·PC 종료 후 서비스는 별도 호스팅 문제 |
| 검색 학습·확장 | Elasticsearch 버전 투영·ngram 분석기·비동기/재색인·Mongo 권한 재검증 | 실제 선택 인프라 설정, eventual consistency·인덱스 운영/장기 용량 확인 |

파일당 10MiB·계정당 100MiB, UI 메모리 최근 1,000개, mesh 최대 4명, 공유 미디어 상태 최대 64세션은 **구현상 제한**이며 벤치마크로 측정한 수용량이 아닙니다. 첨부 quota/고아 청크 및 보관 파일 정비, Jackson 3 완전 전환, 더 큰 통화방의 SFU, 운영 요청 제한/HA 정책은 추가 운영·확장 범위입니다.

## Tests & Verification

실행 결과와 환경/fixture 범위는 [ROADMAP_VALIDATION.md](docs/ROADMAP_VALIDATION.md)에 기록합니다. 기존 [REFACTOR_VALIDATION.md](docs/REFACTOR_VALIDATION.md) 수치는 **이전 소스**의 기록입니다. 테스트 파일이 존재한다고 통과한 것으로 표시하지 않습니다.

```powershell
$env:MONGODB_TEST_URI = "mongodb://localhost:27017"
.\gradlew.bat test bootJar
node --test (Get-ChildItem .\src\test\js\*.cjs).FullName
python -m unittest discover -s src/test/ops -v
```

Mongo 통합 테스트는 환경변수가 없으면 건너뜁니다. 실제 브라우저 테스트는 로컬 개발 서버/Playwright가 필요합니다. CI는 회원가입·작업 화면·1:1·다인 미디어와 선택적 인프라 검증을 분리합니다. 가상 마이크·카메라는 실제 RTP/영상 프레임 검사를 하지만 물리 음질은 확인하지 못합니다. 화면 공유 fixture 및 OS Notification 대역은 실제 OS UI 검증과 구분합니다.

## Project Structure & Documents

```text
src/main/java/com/individual/messenger/
  controller/    HTTP/STOMP/페이지·운영 진입점
  dto/           요청·응답·미디어 DTO
  service/       권한을 포함한 업무·outbox·세션·공유 미디어·투영
  repository/    Mongo 저장/조회 경계
  domain/        기존 문서 모델과 신규 상태 모델
  security/      JWT·STOMP·요청 예산·운영자 인증
  config/        브로커·인덱스·JSON·작업 실행 설정
  crypto/        AES-GCM
  dev/           공개 터널·독립 guardian
src/main/resources/  templates, static, search 인덱스 정의, application.yml
src/test/            Java, Node, 브라우저, 운영 도구 검사
tests/java/          독립 터널 프로세스 검사
scripts/ops/         백업·복구·키 생성·보안 점검
scripts/validate/    제한된 로컬 soak
deploy/              선택적 로컬 인프라/별도 TURN 설정 예제
```

[ROADMAP_IMPLEMENTATION.md](docs/ROADMAP_IMPLEMENTATION.md)는 구현 원리/한계, [OPERATIONS.md](docs/OPERATIONS.md)는 선택 설정/백업/실기기 검사, [ROADMAP_VALIDATION.md](docs/ROADMAP_VALIDATION.md)는 실제 결과를 다룹니다. 기존 DISCORD_MVP·WORKSPACE_V2·VOICE_CALLS·INTELLIJ_PUBLIC_TUNNEL·RENDER_FREE 문서는 해당 단계의 기록도 포함하므로 현재 동작은 이 README와 후속 문서를 우선합니다.

### README 유지 원칙

**기능 추가·변경과 README 갱신은 같은 변경 묶음으로 진행합니다.** 기능·구조·환경 변수·API·테스트·한계를 함께 수정하고, 기존 이력서 소개를 임의로 지우지 않습니다. 구현 예정/코드 구현/실행 검증/배포 완료를 구분합니다. 사용자 데이터·키·SDP/ICE 원문을 문서에 넣지 않습니다.

## Contact

📧 [bluewlsghks@gmail.com](mailto:bluewlsghks@gmail.com) · [Notion 이력서](https://chipped-falcon-031.notion.site/Backend-Developer-7-years-3ed42116bdcf80408ee7ffb8d62d9e7c?source=copy_link)
