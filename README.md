# Messenger (Personal Realtime Chat)

Spring Boot 기반의 개인 메신저 프로젝트입니다.  
WebSocket(STOMP) 실시간 채팅과 MongoDB 저장, JWT 인증을 바탕으로 **1:1(DM) / 그룹 채팅**, **무한 스크롤**, **읽음 처리** 등 메신저 핵심 기능을 구현합니다.

---

## Tech Stack

- **Backend**: Java, Spring Boot, Spring WebSocket(STOMP), Spring Security
- **Database**: MongoDB (Spring Data MongoDB)
- **Auth/Security**: JWT, BCrypt, AES256(AES-GCM)
- **Build/Deploy**: Gradle (Kotlin DSL), (옵션) Docker / Jenkins
- **etc**: REST API, WebSocket Pub/Sub 패턴

---

## Features

### Chat
- 실시간 채팅 (WebSocket + STOMP)
- 1:1(DIRECT) / 그룹(GROUP) 채팅방
- 메시지 저장 및 조회
- 최신 메시지 기준 페이지네이션 / **무한 스크롤** (구현/개선 중)
- 날짜 구분선 (Date Divider) (구현/개선 중)
- **읽음 처리(Read Receipt)** (구현/개선 중)

### Room (DIRECT 중복 방지)
- DM 방은 두 사용자 조합이 **하나의 방만** 생성되도록 보장
- "membersKey" = 두 멤버 ID 정렬 후 "#"로 join
- 중복 생성 시 "DuplicateKeyException" 발생 → 기존 방 재조회

### AI Bot (옵션 기능)
- 채팅에서 "/ai" 명령은 별도 STOMP 엔드포인트로 처리
- AI_BOT 응답만 브로드캐스트하여 일반 채팅과 분리

---

## Project Structure (example)

> 실제 패키지명은 프로젝트 기준으로 확인 후 업데이트하세요.

- "config/" : WebSocket/STOMP 설정, Security 설정
- "api/" or "controller/" : REST / STOMP 메시지 핸들러
- "domain/" : MongoDB Document(Entity)
- "repository/" : Spring Data MongoDB Repository
- "service/" : 비즈니스 로직 (방 생성, 메시지 저장/조회, 읽음 처리 등)

---

## Endpoints

### WebSocket (STOMP)
- **Handshake**
  - "/ws-stomp" (SockJS 지원)

- **Publish (Client → Server)**
  - "/pub/chat.send" : 일반 채팅 메시지 발행
  - "/pub/ai.ask" : "/ai" 요청 발행 (AI_BOT 응답 전용)

- **Subscribe (Server → Client)**
  - "/sub/**" : 토픽 구독 (방/채널 기준으로 사용)

> 구체적인 구독 토픽은 구현에 맞게 아래 예시를 수정하세요.  
예) "/sub/rooms/{roomId}"

### REST API (example)
- "POST /api/auth/register" : 회원가입
- "POST /api/auth/login" : 로그인(JWT 발급)

> 실제 구현된 API 목록이 있다면 Swagger 또는 간단한 표로 정리 추천.

---

## Data Model (MongoDB)

### users (예시)
- "loginId" (unique)
- "passwordHash" (BCrypt)
- "phoneEnc" (AES-GCM)
- "userName"
- "profileMessage"
- "createdAt"

### rooms
- "type" : "DIRECT" | "GROUP"
- "members" : 사용자 ID 배열
- "membersKey" : DIRECT 중복 방지용 키 (예: "A#B")
- "createdAt"

**Index**
- "rooms": "(type, membersKey)" unique

### messages
- "roomId"
- "senderId"
- "senderName"
- "content"
- "createdAt"
- (옵션) "readBy" / "readCount" / "lastReadAt" 등 읽음 처리 필드 (설계에 따라)

---

## Local Run

### 1) Requirements
- JDK 21+
- MongoDB

### 2) Config
"application.yml"에 민감정보를 직접 커밋하지 말고, 환경변수로 주입하는 것을 권장합니다.

예시 환경변수:
- "MONGODB_URI"
- "JWT_SECRET"
- "AES_SECRET" (또는 KEY/IV 정책에 맞게)

### 3) Run
"""bash
./gradlew clean build
./gradlew bootRun
