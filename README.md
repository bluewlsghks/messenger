# Messenger (Realtime Chat)

Spring Boot 기반의 개인 메신저 프로젝트입니다.  
**WebSocket(STOMP)** 실시간 채팅과 **MongoDB Atlas**, **JWT 인증 + Spring Security**를 바탕으로 메신저 핵심 기능을 구현합니다.  
또한 `/ai` 명령을 통해 **AI_BOT 자동 응답** 기능을 제공합니다.

---

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.3.3  
- **Realtime**: WebSocket, STOMP  
  - `spring-boot-starter-websocket`, `spring-messaging`  
- **Database**: MongoDB Atlas  
  - `spring-boot-starter-data-mongodb`  
- **Auth/Security**: Spring Security, JWT (jjwt 0.11.5), BCrypt  
- **Template**: Thymeleaf  
- **AI**: OpenAI Java SDK (`com.openai:openai-java:4.16.1`)  
- **Build**: Gradle (Kotlin DSL)

---

## Features

### Realtime Chat
- WebSocket + STOMP 기반 실시간 채팅
- 메시지 MongoDB 저장 및 브로드캐스트
- (진행 중) 무한 스크롤, 날짜 구분선, 읽음 처리

### Rooms / Friends / Messages (REST)
- 로그인 / 회원가입 (JWT 발급)
- 채팅방(Rooms) 관리
- 메시지(Messages) 조회 및 저장
- 친구(Friend) 기능

### AI Bot
- 채팅에서 `/ai` 명령을 통해 AI_BOT 응답 생성
- AI 전용 STOMP 엔드포인트(`/pub/ai.ask`) 지원
- 최근 메시지 N개(예: 50개)를 컨텍스트로 사용해 응답 생성

---

## WebSocket (STOMP)

### Handshake Endpoint
- `GET /ws-stomp`
  - SockJS 사용
  - 개발 단계 CORS 허용: `AllowedOriginPatterns("*")`

### Prefix
- **Publish (Client → Server)**: `/pub/**`  
- **Subscribe (Server → Client)**: `/sub/**`

### Message Flow

#### 1) 일반 채팅 메시지
- Client publish: `/pub/chat.send`  
- Server broadcast: `/sub/chat/{roomId}`  

Payload 예시:
```json
{
  "roomId": "ROOM_ID",
  "senderId": "USER_ID",
  "senderName": "USER_NAME",
  "content": "hello"
}
2) AI 전용 요청
Client publish: /pub/ai.ask

Server broadcast (AI_BOT 응답): /sub/chat/{roomId}

AI_BOT 메시지도 일반 채팅과 동일한 토픽(/sub/chat/{roomId})으로 브로드캐스트됩니다.

Security / Access Rules
Public (Permit All)
OPTIONS /**

인증 관련: /login, /register, /api/auth/**

정적 리소스: /js/**, /css/**, /images/**, /favicon.ico, /webjars/**

WebSocket: /ws-stomp/**

HTML 뷰: /, /home, /rooms, /friends, /chat/**

Protected (JWT Required)
/api/**

세션은 사용하지 않고 STATELESS로 동작합니다.
JwtAuthFilter가 JWT를 검증합니다.

REST API (High Level)
실제 경로는 컨트롤러 구현에 맞게 조정하세요.

Auth

POST /api/auth/login

POST /api/auth/register

Rooms

GET /api/rooms

POST /api/rooms

Messages

GET /api/messages/{roomId}

Friends

/api/friends/**

OpenAI

(STOMP 기반) /pub/ai.ask

Data Model (MongoDB)
rooms

type (DIRECT | GROUP)

members

membersKey (DIRECT 중복 방지용 키)

createdAt

messages

roomId

senderId

senderName

content

createdAt

Local Run
Requirements
JDK 21

MongoDB Atlas 연결 정보

Recommended Configuration
민감정보는 커밋하지 말고 환경변수로 관리하는 것을 권장합니다.

예시:

SPRING_DATA_MONGODB_URI

JWT_SECRET

OPENAI_API_KEY

Build & Run
bash
코드 복사
./gradlew clean build
./gradlew bootRun
Notes
운영 환경에서는 CORS를 * 대신 명시 도메인으로 제한하는 것을 권장합니다.

senderId는 운영 환경에서 JWT 기반으로 서버에서 검증하는 구조가 안전합니다.

License
Personal project.
