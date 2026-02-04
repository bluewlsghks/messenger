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

## Architecture

본 프로젝트는 **REST API + WebSocket(STOMP)** 를 결합한 구조로 설계되었습니다.

### 1. 인증 흐름
1. 사용자는 REST API(`/api/auth/login`)를 통해 로그인합니다.
2. 서버는 JWT를 발급합니다.
3. 이후 모든 `/api/**` 요청은 JWT를 통해 인증됩니다.
4. WebSocket 연결 시에도 JWT 기반으로 사용자 식별을 수행하도록 설계되어 있습니다.

### 2. 메시지 처리 흐름
1. 클라이언트는 `/pub/chat.send` 로 메시지를 발행(Publish)합니다.
2. 서버는 메시지를 MongoDB에 저장합니다.
3. 저장된 메시지를 `/sub/chat/{roomId}` 토픽으로 브로드캐스트합니다.
4. 같은 방을 구독 중인 모든 클라이언트가 메시지를 수신합니다.

### 3. AI 처리 흐름
1. 사용자가 `/ai` 명령 또는 `/pub/ai.ask` 로 질문을 보냅니다.
2. 서버는 최근 메시지 N개를 조회하여 컨텍스트로 구성합니다.
3. OpenAI API를 호출하여 응답을 생성합니다.
4. AI 응답은 일반 메시지와 동일하게 `/sub/chat/{roomId}` 로 브로드캐스트됩니다.

### 4. 데이터 저장 구조
- 채팅방(Room)과 메시지(Message)는 MongoDB 컬렉션으로 분리 저장됩니다.
- 1:1 채팅(DIRECT)의 경우, 중복 방 생성을 방지하기 위해 `membersKey` 기반 구조를 사용합니다.

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

REST API (Table)
Method	Path	Description	Auth
POST	/api/auth/login	로그인	No
POST	/api/auth/register	회원가입	No
GET	/api/rooms	채팅방 목록 조회	Yes
POST	/api/rooms	채팅방 생성	Yes
GET	/api/messages/{roomId}	메시지 조회	Yes
GET	/api/friends	친구 목록 조회	Yes
POST	/api/friends	친구 추가	Yes
POST	/pub/ai.ask (STOMP)	AI 질문 요청	Yes

실제 구현된 API에 맞게 확장 가능합니다.

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
