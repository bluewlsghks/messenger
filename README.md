# 💻 이진환 | Backend Developer

> **복잡한 업무를 이해하고, 데이터가 정확하게 흐르는 백엔드를 만듭니다.**

**7+ years · Java / Spring · ERP / MES · Real-time Systems**  
📧 [bluewlsghks@gmail.com](mailto:bluewlsghks@gmail.com) · 🔗 [Notion 이력서](https://chipped-falcon-031.notion.site/Backend-Developer-7-years-3ed42116bdcf80408ee7ffb8d62d9e7c?source=copy_link)

## 👨‍💻 Developer Profile

ERP 운영에서 쌓은 문제 해결력에, MES·실시간 서비스 설계 경험을 더한 Java/Spring 백엔드 개발자입니다. 인사·근태·복무·전자결재 연계 ERP에서 복잡한 업무 규칙, SQL 성능 병목과 운영 장애를 다뤘으며, 현재는 MES/PMS 생산관리 백엔드와 설비·외부 시스템 연계를 개발하고 있습니다.

개인 프로젝트 **Messenger**에서는 DM·그룹 채팅을 서버·채널형 커뮤니티로 확장하며 접근 권한, 동시 수정 충돌, 조회 경계, 회귀 테스트까지 구현했습니다. 아래에는 개발자 소개와 함께 **실제로 구현한 기능, 근거 코드, 검증 범위, 남은 과제**를 정리했습니다.

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
- **개인 실시간 서비스:** MongoDB, REST/STOMP 공통 인가, 복합 커서, 조건부 원자적 갱신, GitHub Actions·브라우저 회귀 테스트.

회사 프로젝트의 기술과 이 저장소의 기술은 구분합니다. **Messenger는 MongoDB·Thymeleaf·Vanilla JavaScript 기반**이며, JPA·Vue 기반 프로젝트가 아닙니다.

### 🛠️ How I Work

**코드가 동작하는 이유만큼, 데이터가 맞는 이유를 설명합니다.** 입력부터 저장·집계·외부 연계까지 앞뒤 업무를 확인하고, 오류를 고칠 때는 재현 조건과 영향 범위를 함께 살핍니다. 구조를 선택한 이유와 예외 상황, 검증 방법을 설명할 수 있는 개발을 지향합니다.

---

# 📬 Messenger · 실시간 메신저

**Java 21 · Spring Boot · MongoDB 기반의 실시간 메신저 개인 프로젝트**입니다.
WebSocket/STOMP와 JWT 인증을 기반으로 시작해, **1:1 DM·그룹 대화·Discord형 서버/텍스트 채널을 하나의 작업 화면으로 통합**했습니다. 메시지 수정·삭제·검색, 읽음 처리, 새 메시지 알림과 선택적 AI 응답까지 구현했습니다.

> **문서 기준:** 2026-10-02, `master`의 기능 병합 커밋 `9ba7682` 기준.
> [PR #1](https://github.com/bluewlsghks/messenger/pull/1)은 `master`에 병합되었습니다. 실행 안내도 `master` 기준입니다. 아래 테스트 수치는 각 검증 시점의 소스에 대한 결과이며, 이번 문서 수정으로 새 운영 검증을 완료했다는 의미는 아닙니다.
> 텍스트 메신저 프로젝트로, Discord 전체 기능이나 음성·영상 통화, 실제 서비스 배포 완료를 의미하지 않습니다.

[Resume Summary](#resume-summary) · [기능](#features) · [구조](#architecture) · [API](#rest-api) · [실행](#getting-started) · [검증](#tests--verification) · [남은 과제](#limitations--roadmap)

---

## Resume Summary

> Spring Boot와 MongoDB 기반 실시간 메신저를 설계·구현하고, 기존 DM/그룹 채팅을 서버·채널 중심의 커뮤니티 구조로 확장한 개인 프로젝트입니다. REST와 STOMP의 공통 인증·인가 및 메시지 처리 로직을 구성하고, 사용자별 실시간 이벤트, 읽음 상태, 메시지 변경 충돌 처리와 브라우저 회귀 테스트를 구현했습니다.

### 주요 구현 및 기술 경험

- **실시간 통신:** REST 저장 응답과 STOMP 이벤트 수신을 결합하고, 통합 화면에서 연결을 재사용하도록 구성했습니다. 재연결, 토큰 변경, 브라우저 뒤로가기 캐시 복원도 처리합니다.
- **인증·인가:** JWT/BCrypt 인증에 더해 메시지 조회·전송·읽음, STOMP 구독과 실제 수신 단계에서 방/채널 멤버십을 검사하고 발신자 정보를 서버에서 결정합니다.
- **데이터 모델링:** 서버 문서에 멤버·채널을 포함하고 기존 메시지 저장소를 재사용했습니다. DM 조합 키와 부분 고유 인덱스, 시간+ID 복합 커서, 조건부 원자적 갱신을 적용했습니다.
- **대화 기능:** 본인 메시지 수정·소프트 삭제, 버전 충돌 검증, 대화별 검색, 읽음 상태와 미확인 메시지 집계, 개인 이벤트 기반 알림을 구현했습니다.
- **문제 해결과 사용성:** 친구 목록 Principal 오류, ID 대소문자 충돌, 동일 시각 메시지 페이지 경계 문제를 보강하고 한국어 IME·초안·실패 입력 보존·반응형 화면을 반영했습니다.
- **AI·검증·배포 준비:** 선택적 OpenAI 연동, 비동기 실행과 동시 요청 제한, Java/JavaScript/브라우저 테스트 및 CI, Windows 공개 접속 스크립트와 Docker 배포 구성을 추가했습니다.

## Tech Stack

| 영역 | 저장소에서 사용하는 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.3.3, Spring Web, Bean Validation |
| Realtime | Spring WebSocket/Messaging, STOMP, SockJS, Spring Simple Broker |
| Database | MongoDB, Spring Data MongoDB, MongoTemplate; Atlas 연결 가능 |
| Security | Spring Security, JWT (`jjwt 0.11.5`, HS256), BCrypt, 개인정보 AES-GCM 암호화 |
| Frontend | Thymeleaf, Vanilla JavaScript, HTML/CSS, 반응형 다크/라이트 UI |
| Browser libraries | WebJar로 제공하는 `@stomp/stompjs 7.3.0`, `sockjs-client 1.6.1` |
| AI | OpenAI Java SDK `4.16.1`, Responses API; 소스에 설정된 모델 `gpt-4.1-mini` |
| Build / Test | Gradle Kotlin DSL, JUnit 5, Mockito, Node 테스트, Python Playwright Chromium |
| CI / Deployment assets | GitHub Actions, Docker 멀티 스테이지 빌드, PowerShell, Render Blueprint |

버전 근거: [build.gradle.kts](build.gradle.kts). 애플리케이션 소스는 Java이며, **Vue/React 프런트엔드나 별도 npm 빌드가 필요한 구조는 아닙니다.** 현재 작업 화면의 CSS와 STOMP/SockJS 라이브러리는 애플리케이션에서 제공합니다.

## Features

| 기능 | 현재 구현 내용 |
|---|---|
| 통합 작업 화면 | 친구·DM·그룹·서버/채널 이동, 서버 레일·대화 목록·대화 영역·멤버 패널, 다크/라이트 테마, 모바일 접이식 메뉴, 검색과 키보드 탐색 |
| 회원·프로필 | 회원가입/로그인, JWT 발급, 내 정보 조회, 표시 이름 변경; 비밀번호 BCrypt 저장, 전화번호 암호화 저장 |
| 친구·DM·그룹 | 친구 추가/목록/삭제, 이름·ID 검색, DM 생성 및 기존 방 재사용, 본인 포함 3~50명 그룹 생성 |
| 서버·채널·초대 | 서버 생성 시 `#일반` 자동 생성, 소유자의 채널 생성/초대 코드 발급, 코드로 참여, 서버 멤버 조회 |
| 대화 내역·읽음 | 메시지 저장 및 실시간 수신, 날짜 구분, 복합 커서 기반 이전 내역 더 보기, 화면에 보이는 메시지 읽음 처리, 읽음 표시와 DB 기반 안 읽은 개수 집계 |
| 메시지 작업 | 본인 메시지 수정·소프트 삭제, 수정 표시/삭제 자리 표시, 버전 충돌 검사, 현재 대화 검색, 복사, 기본 이모지 |
| 작성·연결 UX | 대화별 초안, 글자 수 표시, 한국어 IME 조합 중 Enter 전송 방지, Shift+Enter 줄바꿈, 전송 실패 시 입력 보존, 새 메시지 위치 이동, 재연결 |
| 알림 | 개인 이벤트 피드, 앱 안 팝업, 이번 접속의 알림 센터, 대화/서버/전체 안 읽은 배지, 대화별 음소거, 선택적 데스크톱 알림·알림음·본문 미리보기 |
| AI Bot | `/ai 질문` 또는 STOMP `/pub/ai.ask`, 최근 대화 문맥 활용, 비동기 응답 생성, `AI_BOT` 메시지 저장 및 전달 |

**기능 범위**

친구 추가는 **상호 즉시 등록** 방식이며 승인/거절·차단은 아직 없습니다. 친구를 삭제해도 기존 대화와 메시지는 유지됩니다. 초대 코드는 7일 동안 재사용할 수 있고, 서버당 채널 50개·멤버 500명 제한이 있습니다. 이는 기능상 제한이지 동시 접속 성능 측정값은 아닙니다.

알림은 **메신저 화면이 열려 있고 실시간 연결이 유지되는 동안** 제공됩니다. 탭/브라우저 종료 후 Web Push는 미구현입니다. 데스크톱 알림·알림음·본문 미리보기는 기본 꺼짐이며, 시스템 알림은 사용자 허용과 HTTPS/localhost 환경을 전제로 합니다. 알림 센터 기록은 현재 접속 중 메모리에 보관하지만, 안 읽은 배지는 DB에서 다시 계산합니다.

AI는 기본 비활성입니다. 활성화하면 최근 메시지 최대 50개 조회 결과에서 현재 질문을 제외한 문맥과 질문을 외부 OpenAI API에 전달합니다. **대화 참여자에게 외부 전송을 안내하고 사용 정책을 정한 후 활성화해야 합니다.** 프로세스당 동시 실행은 4개로 제한하지만 사용자별 사용량·비용 한도는 별도 과제입니다.

## Architecture

### 메시지 처리 흐름

```text
브라우저 통합 작업 화면
  ├─ REST POST /api/messages ──────┐
  └─ 기존 STOMP SEND /pub/chat.send ┤
                                  v
                         ChatMessagingService
                           ├─ ChatAccessService: 로그인 사용자·멤버십 확인
                           └─ MessageService: 검증 후 MongoDB 저장
                                ├─ /sub/chat/{roomId}: 기존 대화 토픽
                                └─ 저장 이벤트 → MessageNotificationListener
                                                 → ConversationEvents
                                                 → /user/queue/events
                                                      ↓
                                               통합 화면·알림 갱신

읽음 변경: POST /api/messages/read → DB 갱신 → /sub/chat/{roomId}/read
AI 명령: 공통 전송 경로 → 별도 실행기 → OpenAI → AI_BOT 저장/전달
```

현재 통합 화면은 **REST 응답으로 저장 결과를 확인하고**, `/user/queue/events`로 생성·수정·삭제 이벤트를 수신합니다. 과거 클라이언트용 STOMP 전송과 방별 메시지 토픽도 유지합니다. 화면은 메시지 ID와 버전을 기준으로 결과를 병합합니다.

### 설계 포인트

| 문제 / 선택 | 구현 방식 및 근거 코드 |
|---|---|
| REST/STOMP 간 로직 중복 | [ChatMessagingService](src/main/java/com/individual/messenger/service/ChatMessagingService.java)에서 발신자·권한·내용 검증과 AI 진입점을 공유 |
| 서버·채널 확장 | [ChatServer](src/main/java/com/individual/messenger/domain/ChatServer.java)에 소유자·멤버·채널을 포함하고 `Message.roomId`에 방 ID 또는 채널 ID를 저장 |
| DM 중복 및 ID 충돌 | [Room](src/main/java/com/individual/messenger/domain/Room.java)의 대소문자를 보존하는 정렬·Base64 조합 키, [RoomService](src/main/java/com/individual/messenger/service/RoomService.java)의 중복 키 경쟁 처리 |
| 동일 시각 메시지의 페이지 경계 | [MessageService](src/main/java/com/individual/messenger/service/MessageService.java)의 `createdAt + _id` 복합 정렬/커서와 관련 인덱스 |
| 수정·삭제 경쟁 | [MessageActionController](src/main/java/com/individual/messenger/api/MessageActionController.java)의 작성자·방·현재 버전 조건부 `findAndModify`, 충돌 시 HTTP 409 |
| 초대·채널 생성 경쟁 | [ChatServerService](src/main/java/com/individual/messenger/service/ChatServerService.java)의 조건부 갱신, `$addToSet` 참여, 난수 코드의 SHA-256 해시 저장 및 만료 검증 |
| 실시간 알림 대상 제한 | [ConversationEvents](src/main/java/com/individual/messenger/service/ConversationEvents.java)에서 실제 멤버에게 개인 이벤트를 발행하고 수신 단계에서 재인가 |

## Security & Access Rules

HTTP 인증은 `Authorization: Bearer <JWT>` 헤더를 사용하며 세션은 `STATELESS`입니다. 기본 액세스 토큰 만료는 120분입니다. `/api/auth/refresh`는 **이미 유효한 액세스 토큰으로 새 액세스 토큰을 발급하는 경로**이며, 별도 Refresh Token 회전/폐기 구조는 아닙니다.

`POST /api/auth/login`, `POST /api/auth/register`, 로그인/가입·작업 화면 HTML, 정적 리소스 및 SockJS 핸드셰이크 경로는 공개합니다. **HTML이나 `/ws-stomp`가 공개되어 있어도 데이터 접근과 STOMP CONNECT가 인증 없이 허용되는 것은 아닙니다.**

[ChatAccessService](src/main/java/com/individual/messenger/security/ChatAccessService.java)와 [StompSecurityInterceptor](src/main/java/com/individual/messenger/security/StompSecurityInterceptor.java)는 메시지 조회·전송·읽음·검색·변경, 구독 및 실제 전달에서 사용자/멤버십을 검사합니다. 발신자 ID·이름은 클라이언트 입력을 신뢰하지 않고 서버의 사용자 정보로 결정합니다. STOMP SEND 목적지도 허용 목록으로 제한합니다.

[SecurityConfig](src/main/java/com/individual/messenger/security/SecurityConfig.java)와 [WebSocketConfig](src/main/java/com/individual/messenger/config/WebSocketConfig.java)는 동일한 명시적 Origin 목록을 사용합니다. 현재 HTTP 인증 필터는 쿠키 인증을 사용하지 않습니다. 사용자 입력은 통합 화면의 DOM `textContent`로 표시하며, 메시지 검색어는 정규식 문법이 아닌 리터럴 문자열로 처리합니다.

[CryptoService](src/main/java/com/individual/messenger/crypto/CryptoService.java)는 전화번호 등 해당 개인정보 필드를 AES-GCM으로 암호화합니다. **채팅 본문 암호화나 종단 간 암호화(E2EE)를 구현한 것은 아닙니다.** 키·DB 비밀번호는 환경 변수로 주입하며 실제 값을 README나 Git에 넣지 않습니다.

## REST API

아래 표에서 로그인/회원가입을 제외한 데이터 API는 Bearer JWT가 필요하며, 대상 자원별 권한 검사도 적용됩니다.

| Method | Path | 기능 |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | 회원가입 / 로그인 |
| POST | `/api/auth/refresh` | 유효한 인증으로 액세스 토큰 재발급 |
| GET / PATCH | `/api/users/me` | 내 정보 조회 / 표시 이름 변경 |
| GET / POST | `/api/friends` | 친구 목록 / 상호 친구 추가 |
| DELETE | `/api/friends?friendId={id}` | 상호 친구 관계 삭제; 대화는 유지 |
| GET | `/api/rooms`, `/api/rooms/my` | 내가 참여한 DM/그룹 목록 |
| POST | `/api/rooms/dm`, `/api/rooms/group` | DM 생성·재사용 / 그룹 생성 |
| POST | `/api/rooms` | 기존 DIRECT/GROUP 생성 경로 |
| GET / POST | `/api/servers` | 참여 서버 목록 / 서버 생성 |
| GET | `/api/servers/{serverId}` | 서버·채널·멤버 조회 |
| GET / POST | `/api/servers/{serverId}/channels` | 채널 목록 / 소유자의 채널 생성 |
| POST | `/api/servers/{serverId}/invites` | 소유자의 초대 코드 발급 |
| POST | `/api/servers/join` | 초대 코드로 서버 참여 |
| POST | `/api/messages` | 메시지 저장 및 실시간 전달 |
| GET | `/api/messages/{roomId}?before=...&beforeId=...&limit=100` | 복합 커서 기반 과거 내역 |
| GET | `/api/messages?roomId=...&page=0&size=20` | 기존 페이지 방식 조회 |
| POST | `/api/messages/read` | 해당 방의 메시지 읽음 처리 |
| PATCH | `/api/messages/{roomId}/{messageId}` | 본인 메시지 수정; `content`, `version` 필요 |
| DELETE | `/api/messages/{roomId}/{messageId}?version=...` | 본인 메시지 소프트 삭제 |
| GET | `/api/messages/{roomId}/search?q=...` | 현재 대화의 삭제되지 않은 메시지 검색; 최대 50개 |
| GET | `/api/notifications/unread` | 접근 가능한 대화별 미확인 메시지 수 |

기존 `/api/dm/start`, `/api/dm/{roomId}/send`, `/api/dm/{roomId}/read`도 유지합니다. 현재 작업 화면의 읽음 표시는 `messages.readBy`를 사용하며, 레거시 DM의 `read_cursors`와는 별도 구조입니다.

### WebSocket / STOMP

연결 엔드포인트는 `/ws-stomp`이며 SockJS를 사용합니다. STOMP CONNECT 헤더에도 Bearer JWT를 전달해야 합니다. **아래 SEND 경로는 REST POST API가 아닙니다.**

| 구분 | 목적지 | 용도 |
|---|---|---|
| SEND | `/pub/chat.send` | 일반 메시지 전송 |
| SEND | `/pub/ai.ask` | AI 질문 전송; 사용자 질문도 저장 |
| SUBSCRIBE | `/sub/chat/{roomId}` | 방/채널 메시지 및 변경 수신; 기존 클라이언트용 |
| SUBSCRIBE | `/sub/chat/{roomId}/read` | 읽음 변경 수신 |
| SUBSCRIBE | `/user/queue/events` | 통합 화면의 `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_DELETED` |
| SUBSCRIBE | `/user/queue/errors` | 개인 채팅 요청 오류 |

일반 메시지 요청 본문은 REST와 STOMP 모두 다음 형태입니다. `senderId`나 `senderName`을 지정할 필요가 없습니다.

```json
{
  "roomId": "ROOM_OR_CHANNEL_ID",
  "content": "안녕하세요!"
}
```

메시지는 1~4,000자, 내역 조회와 한 번의 읽음 처리는 최대 100개입니다. `beforeId`를 보낼 때는 해당 메시지의 `createdAt`을 `before`로 함께 전달합니다.

## Data Model

| 컬렉션 | 주요 필드 / 역할 |
|---|---|
| `users` | `loginId`, `userName`, `passwordHash`, `phoneEnc`, `createdAt`; 인증 및 프로필 |
| `friends` | `ownerId`, `friendId`, `createdAt`; 상호 관계를 방향별 문서로 저장 |
| `rooms` | `type` (`DIRECT` / `GROUP`), `members`, `membersKey`, `createdAt` |
| `chat_servers` | `name`, `ownerId`, `members`, `channels[{id,name,createdAt}]`, `createdAt` |
| `server_invites` | 코드의 SHA-256 해시를 `_id`로 저장, `serverId`, `createdBy`, `expiresAt` |
| `messages` | `roomId`, `senderId`, `senderName`, `content`, `createdAt`, `readBy`, `version`, `editedAt`, `deletedAt` |
| `read_cursors` | 레거시 DM용 `roomId`, `username`, `lastReadAt` |

[ServerIndexConfiguration](src/main/java/com/individual/messenger/config/ServerIndexConfiguration.java)은 서버 멤버/채널 조회 인덱스, 초대 만료 TTL 인덱스, `roomId + createdAt + _id` 커서 인덱스, 문자열 `membersKey` 대상 부분 고유 인덱스를 추가합니다. TTL 삭제 시점에 의존하지 않고 초대 참여 API에서도 만료를 검사합니다.

새 GROUP 방은 독립적인 `group:` 키를 사용합니다. 기존 문자열 `membersKey` 중복이 있으면 새 인덱스 생성에 실패할 수 있으므로 개발 DB에서 먼저 점검해야 합니다. **기존 데이터/인덱스를 자동 삭제하거나 과거 ID 대소문자를 임의로 바꾸지 않습니다.**

## Recent Improvements & Troubleshooting

| 변경 / 문제 | 현재 반영 내용 |
|---|---|
| 친구 목록 `INTERNAL_ERROR` | 문자열 Principal에 `username` 속성을 요구하던 방식 대신 공통 사용자 확인 경로 사용. 이름 일괄 조회, 중복 표시 제거, 불필요한 레거시 날짜 매핑 회피 |
| 친구·프로필 경계 조건 | 자기 자신/없는 사용자 추가 거부, 친구 삭제 확인, 전화번호가 없는 계정의 프로필 조회 처리 |
| DM·그룹 키 충돌 | ID 대소문자 보존, 기존 DM 참여자 일치 확인, 신규 그룹 독립 키 및 부분 고유 인덱스 |
| 과거 내역 순서·경계 누락 | 시간+ID 복합 커서, 메시지 ID 기반 병합과 정렬 |
| 중복 팝업·오래된 변경 이벤트 | 본인/집중해서 보는 대화의 팝업 억제, 수정·삭제를 신규 알림에서 제외, 버전 기반 병합 |
| 사용자 입력 유실·한글 Enter | 대화별 초안, 실패 입력 보존, IME 조합 확인, Shift+Enter 줄바꿈 |
| 연결·화면 복원 | 자동 재연결, 다른 탭 토큰 변경 처리, 뒤로가기 캐시 복원 시 연결 재개, 멤버 패널을 열 때 서버 정보 재조회 |
| 외부 HTTPS 접속의 Origin 불일치 | 실제 공개 주소를 HTTP CORS와 SockJS 허용 목록에 적용하는 실행 스크립트 및 회귀 테스트 추가 |
| 배포 준비 | 비루트 사용자 Docker 실행, 포트/Origin/필수 변수 검증, Render Blueprint, 공개 실행 시 AI 비활성화 |

외부 URL에서 로그인에 403이 발생하면 현재 URL의 **Origin(경로 없는 `https://호스트`)**과 `APP_ALLOWED_ORIGINS`, 서버 재시작 여부를 먼저 확인합니다. 이것이 모든 403의 원인이라는 뜻은 아닙니다. 임시 주소 변경 시 이전 주소 설정을 그대로 사용하거나 `*`로 전체 허용하지 말고 [공개 접속 안내](docs/PUBLIC_ACCESS.md)에 따라 정확한 주소를 적용합니다.

## Getting Started

### 준비 및 소스 받기

Java 21과 접근 가능한 **개발용 MongoDB**가 필요합니다. Gradle Wrapper가 포함되어 있어 Gradle 별도 설치는 필요하지 않습니다. Node/Python/Playwright는 해당 테스트를 실행할 때 사용합니다.

```bash
git clone --branch master https://github.com/bluewlsghks/messenger.git
cd messenger
```

이미 저장소가 있다면 로컬 변경을 먼저 커밋/보관한 뒤 `git fetch origin`, `git switch master`, `git pull --ff-only origin master` 순서로 갱신합니다. 로컬 수정사항이나 분기 차이로 명령이 실패하면 강제 초기화하지 말고 먼저 변경 내역을 확인합니다.

### 환경 변수

| 변수 | 필수 여부 / 설명 |
|---|---|
| `MONGODB_URI` | 필수. 개발 MongoDB 연결 URI |
| `APP_AES_KEY_BASE64` | 필수. Base64 인코딩 AES 키; 구현은 16/24/32바이트 키를 허용. 신규 환경은 32바이트 키 사용 권장 |
| `APP_JWT_SECRET_BASE64` | 필수. JWT 서명용 32바이트 이상 키의 Base64 값; AES 키와 별도 관리 |
| `APP_ALLOWED_ORIGINS` | 기본 `http://localhost:8080,http://127.0.0.1:8080`; 실제 접속 Origin을 쉼표로 구분 |
| `APP_OPENAI_ENABLED` | 선택. 기본 `false` |
| `OPENAI_API_KEY` | AI를 활성화할 때만 필요 |
| `MONGODB_TEST_URI` | MongoDB 통합 테스트 실행 시 사용; 운영 DB가 아닌 전용 테스트 MongoDB 연결 |

**기존 DB의 암호화된 개인정보를 읽으려면 기존 AES 키를 그대로 유지해야 합니다.** 이 문서에는 실제 비밀값을 넣지 않습니다. 다음 자리표시자는 자신의 로컬 값으로 교체합니다.

```powershell
$env:MONGODB_URI = "mongodb://localhost:27017/messenger_dev"
$env:APP_AES_KEY_BASE64 = "<기존 또는 신규 개발용 AES 키의 Base64 값>"
$env:APP_JWT_SECRET_BASE64 = "<별도로 관리하는 JWT 서명 키의 Base64 값>"
$env:APP_ALLOWED_ORIGINS = "http://localhost:8080,http://127.0.0.1:8080"
$env:APP_OPENAI_ENABLED = "false"

.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

macOS/Linux에서는 같은 환경 변수를 설정한 후 `bash ./gradlew test bootJar`, `bash ./gradlew bootRun`을 실행합니다. 기본 접속 주소는 `http://localhost:8080`이며 로그인 후 통합 화면으로 이동합니다. 일반 메신저 실행에 AI 키는 필요하지 않습니다.

### 외부 접속 및 배포 구성

[PUBLIC_ACCESS.md](docs/PUBLIC_ACCESS.md)는 `scripts/Start-Public.ps1`을 이용한 Windows 개발 PC 공개 접속을 설명합니다. IntelliJ의 기존 실행 설정을 사용하는 `-TunnelOnly` 모드도 있습니다. PC·앱·터널이 중지되면 이 방식의 접속은 유지되지 않습니다.

[RENDER_FREE.md](docs/RENDER_FREE.md)는 저장소의 `Dockerfile`, `scripts/start-render.sh`, `render.yaml`을 설명합니다. Blueprint에는 `plan: free`, 자동 배포 비활성화가 명시되어 있고, 실행기는 정확한 서비스 Origin과 포트를 적용하며 AI를 비활성화합니다. **배포 파일과 CI 검증이 있다는 사실은 실제 클라우드 서비스 생성·상시 운영 완료를 뜻하지 않습니다.** 템플릿 자체는 서비스를 생성하지 않으며, 적용 전 요금/제공 조건을 별도로 확인해야 합니다.

## Tests & Verification

### 확인된 결과

소스 커밋 `1a1717c`에 연결된 **2026-09-30 [Messenger CI 실행](https://github.com/bluewlsghks/messenger/actions/runs/36672680708)**과 `messenger-validation` 아티팩트를 기준으로 정리했습니다. 이 아티팩트의 테스트용 병합 커밋과 개발 브랜치 커밋은 동일한 소스 트리를 가집니다.

| 검증 | 결과 / 확인 범위 |
|---|---|
| Java 단위 + 실제 MongoDB/HTTP 통합 테스트 | CI 보고서 기준 **56개, 실패 0, 건너뜀 0** |
| 실제 Chromium 브라우저 시나리오 | CI 보고서 기준 **10개**, 처리되지 않은 JavaScript 오류 0 |
| `test bootJar` | 해당 CI 성공 |
| JavaScript 회귀 테스트 | 2026-10-02 검증 아티팩트의 소스로 재실행: **19개 통과** |
| 배포 설정·실행기 오프라인 테스트 | 2026-10-02 검증 아티팩트의 소스로 재실행: **9개 통과** |
| 공개 접속 스크립트 | 해당 소스의 Windows PowerShell 5.1/7 CI 성공 |
| Docker 배포 스모크 | [Render 구성 CI](https://github.com/bluewlsghks/messenger/actions/runs/36672680853) 성공; MongoDB와 512MB 제한 컨테이너 검증. 실제 Render 배포/부하 시험은 아님 |

브라우저 테스트는 다중 계정 로그인, 친구·DM, 서버·채널·초대, 비멤버 거부, STOMP 알림, 읽음/배지, 초안, 수정·삭제·검색, 프로필/멤버, 테마 및 모바일 메뉴를 확인합니다. **네이티브 Notification 생성자·권한 응답과 일부 백그라운드 상태는 대역을 사용했으므로 실제 OS 팝업 표시는 검증 결과에 포함하지 않습니다.** 실제 외부 AI 응답이나 운영 환경의 TLS·프록시·장시간 연결·부하도 이 결과만으로 보장하지 않습니다.

### 테스트 실행

```powershell
# 반드시 전용 테스트 MongoDB를 사용합니다.
$env:MONGODB_TEST_URI = "mongodb://localhost:27017"
.\gradlew.bat test bootJar
node --test (Get-ChildItem .\src\test\js\*.cjs).FullName
```

`MONGODB_TEST_URI`가 없으면 관련 MongoDB/HTTP 통합 테스트는 건너뜁니다. 브라우저·배포·PowerShell 검증 명령은 [.github/workflows](.github/workflows)에 있으며, Java 테스트만 실행했다고 모든 브라우저 검증까지 수행되는 것은 아닙니다.

## Limitations & Roadmap

| 영역 | 남은 과제 |
|---|---|
| 메시지 전달 신뢰성 | 클라이언트 요청 ID 기반 저장 멱등성, DB 저장/이벤트 발행 간 영속 outbox, 장시간 오프라인 전체 재생. 현재 ID 기반 중복 **표시** 방지는 중복 **저장** 방지와 다름 |
| 알림·상태·커뮤니티 | 종료된 브라우저의 Web Push, 실제 온라인/입력 중 상태, 친구 승인·차단, 서버 탈퇴·강퇴·삭제/소유권 이전, 초대 철회와 세부 역할/채널별 권한 |
| 대화 확장 | 첨부파일, 답장·스레드·멘션, 음성·영상·화면 공유, 대용량 내역 UI 가상화 |
| 인증·데이터 정비 | Refresh Token 회전/폐기, 사용자별 요청 제한, 가입 입력 정규화/중복 저장 경로 정비, 레거시 개인정보·ID 및 고유 인덱스 점검, 읽음 모델 통합 |
| 운영·확장 | 분산 브로커/다중 인스턴스, 모니터링·백업/복구, 의존성 보안 검토, 실제 운영망/OS 알림과 부하·장기 연결 검증 |
| 검색 학습·확장 계획 | 현재 검색은 MongoDB 기반. Elasticsearch 검색과 비동기 인덱싱, Index/Analyzer 설계는 후속 학습·적용 과제 |

현재 Spring 인메모리 브로커와 JVM 세션 맵은 단일 인스턴스를 전제로 합니다. 재연결 시 최신 100개 메시지를 병합하지만 모든 오프라인 구간을 자동 복구하지는 않습니다. 테스트 통과를 운영 보안 감사나 무중단 전달 보장으로 표현하지 않습니다.

## Project Structure & Documents

```text
src/main/java/com/individual/messenger/
  api/       REST 및 STOMP 진입점
  security/  JWT, HTTP 보안, 공통 인가, STOMP 인터셉터
  service/   친구·방·서버·메시지·개인 이벤트·AI 처리
  domain/    MongoDB 문서 모델
  repo/      MongoRepository 인터페이스
  config/    WebSocket, 인덱스, AI 실행기 등
  crypto/    개인정보 암호화
  web/       Thymeleaf 페이지 라우팅
  rt/        기존 SSE 관련 코드
src/main/resources/
  templates/workspace.html   통합 작업 화면
  static/js/                 인증·연결·대화·알림·화면 로직
  static/css/                화면 스타일
src/test/                    Java, JavaScript, 브라우저, 배포, PowerShell 테스트
scripts/                     공개 접속 및 배포 실행기
.github/workflows/           CI 정의
```

| 문서 | 내용 |
|---|---|
| [DISCORD_MVP.md](docs/DISCORD_MVP.md) | 초기 서버/채널 도입 당시 구조 분석과 기존 DB 마이그레이션 주의. 당시 남은 과제 중 일부는 후속 버전에서 구현됨 |
| [WORKSPACE_V2.md](docs/WORKSPACE_V2.md) | 통합 화면·친구 오류 수정·알림·메시지 작업의 상세 설명 |
| [PUBLIC_ACCESS.md](docs/PUBLIC_ACCESS.md) | Windows 개발 PC 공개 접속 및 Origin 설정 |
| [RENDER_FREE.md](docs/RENDER_FREE.md) | Docker/Render 배포 구성과 적용 전 확인 사항 |

## Contact

- Email: [bluewlsghks@gmail.com](mailto:bluewlsghks@gmail.com)
- Resume: [Notion 이력서](https://chipped-falcon-031.notion.site/Backend-Developer-7-years-2f542116bdcf80378ec1e085b2b3bf4f)
