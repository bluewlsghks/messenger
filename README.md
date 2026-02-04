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

**Backend**  
- Java 21, Spring Boot 3.3.3  
- Spring Web, Spring WebSocket, Spring Messaging  

**Realtime**  
- WebSocket, STOMP  

**Database**  
- MongoDB Atlas  
- Spring Data MongoDB  

**Auth / Security**  
- Spring Security  
- JWT (jjwt 0.11.5)  
- BCrypt  

**Template**  
- Thymeleaf  

**AI**  
- OpenAI Java SDK (`com.openai:openai-java:4.16.1`)  

**Build**  
- Gradle (Kotlin DSL)

---

## ✨ Features

**Realtime Chat**
- WebSocket + STOMP 기반 실시간 채팅
- 메시지 MongoDB 저장 및 브로드캐스트
- (진행 중) 무한 스크롤, 날짜 구분선, 읽음 처리

**Rooms / Friends / Messages (REST API)**
- JWT 기반 로그인 / 회원가입
- 채팅방(Room) 관리
- 메시지(Message) 조회 및 저장
- 친구(Friend) 기능

**AI Bot**
- 채팅에서 `/ai` 명령을 통해 AI 응답 생성
- AI 전용 STOMP 엔드포인트 `/pub/ai.ask`
- 최근 메시지 N개(예: 50개)를 컨텍스트로 활용
- AI 응답도 일반 메시지와 동일하게 브로드캐스트

---

## 🧩 Architecture

**인증 흐름**
- `/api/auth/login` → JWT 발급  
- `/api/**` 요청 JWT 인증  
- WebSocket 연결 시 JWT 기반 사용자 식별  

**메시지 흐름**
- Client → `/pub/chat.send`  
- Server → MongoDB 저장  
- Server → `/sub/chat/{roomId}` 브로드캐스트  

**AI 흐름**
- Client → `/pub/ai.ask`  
- OpenAI API 호출  
- `/sub/chat/{roomId}` 응답 전송  

**데이터 구조**
- Room / Message 컬렉션 분리  
- DIRECT 채팅방은 `membersKey` 기반 중복 방지  

---

## 🔌 WebSocket (STOMP)

- Endpoint: `GET /ws-stomp`  
- Publish: `/pub/**`  
- Subscribe: `/sub/**`

---

## 🔁 Message Flow

**일반 채팅 메시지 예시**
{
  "roomId": "ROOM_ID",
  "senderId": "USER_ID",
  "senderName": "USER_NAME",
  "content": "hello"
}

## 🤖 AI 요청

- Client → `/pub/ai.ask`
- Server → `/sub/chat/{roomId}` (AI_BOT 응답)

---

## 🔐 Security / Access Rules

### Public (Permit All)
- `OPTIONS /**`
- `/api/auth/**`
- `/ws-stomp/**`
- `/js/**`, `/css/**`, `/images/**`
- `/`, `/home`, `/rooms`, `/friends`, `/chat/**`

### Protected (JWT Required)
- `/api/**`
- 세션 미사용 (STATELESS)
- `JwtAuthFilter`를 통한 JWT 검증

---

## 📑 REST API

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | /api/auth/login | 로그인 | No |
| POST | /api/auth/register | 회원가입 | No |
| GET | /api/rooms | 채팅방 목록 조회 | Yes |
| POST | /api/rooms | 채팅방 생성 | Yes |
| GET | /api/messages/{roomId} | 메시지 조회 | Yes |
| GET | /api/friends | 친구 목록 조회 | Yes |
| POST | /api/friends | 친구 추가 | Yes |
| POST | /pub/ai.ask | AI 질문 요청 | Yes |

---

## 🗃 Data Model (MongoDB)

### rooms
- `type` (DIRECT | GROUP)
- `members`
- `membersKey`
- `createdAt`

### messages
- `roomId`
- `senderId`
- `senderName`
- `content`
- `createdAt`

---

## 🛠 Trouble Shooting

### DIRECT 채팅방 중복 생성
```java
String[] arr = new String[]{a, b};
Arrays.sort(arr);
room.setMembersKey(String.join("#", arr));
STOMP 오류
라이브러리 로드 순서 수정

WebSocket 연결 후 send 실행

API Key 노출
git rebase로 히스토리에서 키 제거

환경변수 방식으로 전환

.gitignore 정비

WebSocket 인증
Handshake 단계에서 JWT 파싱

메시지 처리 시 인증 정보 활용

📝 Resume Summary
WebSocket(STOMP) 기반 실시간 메신저 시스템을 설계·구현하고
JWT 인증 및 MongoDB 저장 구조와 AI 자동응답 기능을 연동한 개인 프로젝트

주요 구현 내용
Spring Boot 기반 메신저 설계 및 구현

STOMP 기반 실시간 통신 처리

JWT 인증 구조 적용

MongoDB 도큐먼트 모델링

membersKey 기반 DIRECT 채팅방 중복 방지

OpenAI API 연동 AI 자동응답 기능 구현

⚠️ Notes
운영 환경에서는 CORS를 * 대신 도메인 제한 방식으로 설정 권장

senderId는 서버에서 JWT 기반으로 검증하는 구조 권장
