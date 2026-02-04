# 📬 Messenger (Realtime Chat)

Spring Boot 기반의 **실시간 메신저 웹 애플리케이션**입니다.  
WebSocket(STOMP) 기반 채팅과 JWT 인증, MongoDB 저장 구조를 직접 설계·구현하였으며,  
`/ai` 명령을 통해 **AI_BOT 자동 응답 기능**을 제공하는 개인 프로젝트입니다.

> ✔ 실시간 채팅 아키텍처 학습  
> ✔ 인증·보안 구조 이해  
> ✔ MongoDB 기반 메시지 저장 설계  
> ✔ AI 기능 연동(OpenAI API)

---

## 🛠 Tech Stack

### Backend
- Java 21, Spring Boot 3.3.3  
- Spring Web, Spring WebSocket, Spring Messaging  

### Realtime
- WebSocket, STOMP  

### Database
- MongoDB Atlas  
- Spring Data MongoDB  

### Auth / Security
- Spring Security  
- JWT (jjwt 0.11.5)  
- BCrypt  

### Template
- Thymeleaf  

### AI
- OpenAI Java SDK (`com.openai:openai-java:4.16.1`)  

### Build
- Gradle (Kotlin DSL)

---

## ✨ Features

### 🔹 Realtime Chat
- WebSocket + STOMP 기반 실시간 채팅
- 메시지 MongoDB 저장 및 브로드캐스트
- (진행 중)
  - 무한 스크롤
  - 날짜 구분선
  - 읽음 처리(Read Receipt)

### 🔹 Rooms / Friends / Messages (REST API)
- JWT 기반 로그인 / 회원가입
- 채팅방(Room) 관리
- 메시지(Message) 조회 및 저장
- 친구(Friend) 기능

### 🔹 AI Bot
- 채팅에서 `/ai` 명령을 통해 AI 응답 생성
- AI 전용 STOMP 엔드포인트 `/pub/ai.ask`
- 최근 메시지 N개(예: 50개)를 컨텍스트로 활용하여 응답 생성
- AI 응답도 일반 메시지와 동일하게 브로드캐스트 처리

---

## 🧩 Architecture

본 프로젝트는 **REST API + WebSocket(STOMP)** 구조를 결합하여 설계되었습니다.

### 1️⃣ 인증 흐름
- 사용자는 `/api/auth/login`을 통해 로그인
- 서버는 JWT 발급
- 이후 `/api/**` 요청은 JWT 기반 인증
- WebSocket 연결 시에도 JWT 기반 사용자 식별 구조로 설계

### 2️⃣ 메시지 처리 흐름
- 클라이언트 → `/pub/chat.send` 로 메시지 발행
- 서버 → MongoDB에 메시지 저장
- 서버 → `/sub/chat/{roomId}` 로 브로드캐스트
- 같은 방을 구독 중인 클라이언트가 실시간 수신

### 3️⃣ AI 처리 흐름
- 사용자가 `/ai` 명령 또는 `/pub/ai.ask`로 질문 전송
- 서버는 최근 메시지 N개를 조회하여 컨텍스트 구성
- OpenAI API 호출
- AI 응답을 `/sub/chat/{roomId}` 로 브로드캐스트

### 4️⃣ 데이터 저장 구조
- Room 과 Message 컬렉션 분리 저장
- DIRECT(1:1 채팅)의 경우  
  👉 `membersKey` 기반으로 중복 방 생성 방지

---

## 🔌 WebSocket (STOMP)

### Handshake Endpoint
- `GET /ws-stomp`
- SockJS 사용
- 개발 단계 CORS 허용: `AllowedOriginPatterns("*")`

### Prefix
- Publish (Client → Server): `/pub/**`
- Subscribe (Server → Client): `/sub/**`

---

## 🔁 Message Flow

### ✅ 일반 채팅 메시지
- Client → `/pub/chat.send`  
- Server → `/sub/chat/{roomId}`  

Payload 예시:
```json
{
  "roomId": "ROOM_ID",
  "senderId": "USER_ID",
  "senderName": "USER_NAME",
  "content": "hello"
}
✅ AI 전용 요청
Client → /pub/ai.ask

Server → /sub/chat/{roomId} (AI_BOT 응답)

🔐 Security / Access Rules
Public (Permit All)
OPTIONS /**

/api/auth/**

/ws-stomp/**

/js/**, /css/**, /images/**

/, /home, /rooms, /friends, /chat/**

Protected (JWT Required)
/api/**

세션 미사용 (STATELESS)

JwtAuthFilter를 통한 JWT 검증

📑 REST API
Method	Path	Description	Auth
POST	/api/auth/login	로그인	No
POST	/api/auth/register	회원가입	No
GET	/api/rooms	채팅방 목록 조회	Yes
POST	/api/rooms	채팅방 생성	Yes
GET	/api/messages/{roomId}	메시지 조회	Yes
GET	/api/friends	친구 목록 조회	Yes
POST	/api/friends	친구 추가	Yes
POST	/pub/ai.ask (STOMP)	AI 질문 요청	Yes

🗃 Data Model (MongoDB)
rooms
type (DIRECT | GROUP)

members

membersKey (DIRECT 중복 방 방지용 키)

createdAt

messages
roomId

senderId

senderName

content

createdAt

🚀 Local Run
Requirements
JDK 21

MongoDB Atlas

Environment Variables
민감 정보는 커밋하지 않고 환경변수로 관리합니다.

bash
코드 복사
SPRING_DATA_MONGODB_URI
JWT_SECRET
OPENAI_API_KEY
Build & Run
bash
코드 복사
./gradlew clean build
./gradlew bootRun
⚠️ Notes
운영 환경에서는 CORS를 * 대신 명시 도메인으로 제한 권장

senderId는 운영 환경에서 JWT 기반 서버 검증 구조가 안전
