# Discord형 메신저: 구조 분석과 텍스트 서버 MVP

분석 기준: 기존 master `485bfa760cb30d1d99912491b12666dc91be4ec1`.
개발 브랜치: `feat/discord-server-channels`. 기존 배포 환경이나 실제 사용자 DB에는 접속하지 않았다.

## 1. 기존 프로젝트의 실제 구조

Java 21 / Spring Boot 3.3.3 / Gradle Kotlin DSL 기반 단일 애플리케이션이다. 화면은 Vue가 아니라 Thymeleaf HTML과 브라우저 JavaScript이며, MongoDB를 사용한다.

```
src/main/java/com/individual/messenger/
  api/          인증, 친구, 방, 메시지 REST API + STOMP ChatController
  security/     JWT, HTTP Security
  service/      로그인, DM, 방, 메시지, AI 처리
  domain/       User, Room, Message, ReadCursor 등 Mongo 문서
  repo/         MongoRepository 인터페이스
  config/       WebSocket, 암호화, AI 실행기
  crypto/       개인정보 암호화
  rt/           SSE 관련 코드
  web/          HTML 페이지 라우팅
src/main/resources/
  templates/    홈, 친구, 방, 채팅, 로그인, 가입 화면
  static/js/    인증/홈/친구 클라이언트
```

실시간 채팅, JWT 로그인/회원가입, 친구, DIRECT/GROUP 방, 메시지 저장/과거 조회, 읽음 표시, AI 요청 코드가 이미 있었다. 다만 서버(커뮤니티)와 그 하위 채널 모델은 없었다. 기존 읽음 처리와 무한 스크롤은 "전혀 없음"이 아니라 구현되어 있으나 권한/정렬/경계 상황을 보강해야 하는 상태였다.

## 2. 발견한 문제와 이번 변경

| 기존 소스에서 확인한 문제 | 처리 |
|---|---|
| WebSocketConfig에 STOMP CONNECT 토큰을 해석하는 인터셉터가 없음 | CONNECT 인증 및 세션 Principal 설정 |
| ChatController가 senderId/senderName 요청값을 신뢰 | User 저장 정보에서 서버가 결정 |
| MessageController 조회/읽음에 방 참여자 검사 없음 | 모든 해당 경로에 공통 ChatAccessService |
| 옛 `/api/dm/{roomId}/send` 및 read 경로가 me 파라미터를 신뢰 | me를 무시하고 Principal 사용, 공통 전송/권한 검사 |
| 임의 구독, 다른 방 /read 구독 및 브로커 직접 SEND 제한 없음 | 허용 목적지 목록과 방/채널 멤버십 검사 |
| REST/STOMP/옛 DM 전송에 서로 다른 토픽과 중복 코드 | ChatMessagingService → MessageService → `/sub/chat/{id}` 단일 전송 경로 |
| 일반 방 생성 API가 생성자 포함을 확인하지 않음 | 생성자 확인/포함, 실재 사용자 및 그룹 크기 검증 |
| DIRECT 참여자를 소문자로 변경 | 새 DM은 대소문자를 보존하고 충돌 없는 pair key 사용 |
| GROUP의 membersKey=null과 전역 unique 인덱스가 충돌할 수 있음 | 새 그룹마다 독립 키, 문자열 키만 포함하는 부분 unique 인덱스 |
| createdAt 단일 커서로 동일 시각 메시지가 누락될 수 있음 | createdAt+id 복합 커서, 정렬 테스트 |
| 화면의 prepend 순서 및 내역 재병합 로직이 불안정 | 메시지 ID로 병합 후 시간+ID 정렬 |
| 읽음 이벤트에 실제 방에 없는 요청 ID도 포함 | DB에서 해당 방에 매칭된 ID만 갱신/이벤트 발행 |
| OpenAI 키가 없으면 일반 메신저도 시작 불가 | AI 기본 비활성, 키 없이 텍스트 메신저 실행 |
| 쿠키 인증 허용과 CSRF 비활성 조합, CORS 전체 허용 | 헤더 Bearer 전용, 명시적 허용 Origin |
| 공개 refresh 경로에서 auth null 가능, 오류 원문 노출 | refresh 인증 필요, 상태 코드 유지, 내부 오류는 일반 문구 |

위 내용은 실제 코드 경로 검토 결과이며, 전체 보안 감사나 운영 안정성 인증을 의미하지 않는다.

## 3. 추가한 서버/채널 모델

`ChatServer` 한 문서에 ownerId, members, channels를 저장한다. 서버 생성 시 소유자 멤버십과 #일반 채널도 한 번에 삽입한다. 기존 Message.roomId에는 DIRECT/GROUP 방 ID 또는 텍스트 채널 ID를 저장하여 기존 대화 저장소를 재사용한다.

- 서버 소유자: 채널 생성, 초대 코드 발급, 대화 참여.
- 일반 멤버: 서버/채널 조회, 대화 참여.
- 비멤버: 채널 메시지 조회/전송/구독 거부.
- 서버당 채널 50개, 멤버 500명으로 제한한다. 초과 여부는 변경 쿼리 조건에서도 검사한다.
- 채널명 중복과 동시 생성은 같은 서버 문서에 대한 조건부 원자적 갱신으로 방지한다.
- 초대 코드는 32바이트 난수의 URL-safe 문자열이다. DB에는 SHA-256 해시만 저장한다.
- 코드는 7일 동안 여러 사람이 쓸 수 있다. 소지한 로그인 사용자는 참여 가능하며, 반복 참여는 addToSet으로 멤버가 중복되지 않는다.
- TTL 정리와 별개로 API에서 만료 시각을 검사한다. 초대 철회/사용 횟수 제한은 아직 없다.

서버마다 멤버십을 따로 복제하지 않기 위해 소규모 MVP에서는 임베디드 채널을 선택했다. 대규모 서버, 세부 역할, 채널별 권한을 도입할 때는 멤버십과 채널을 별도 컬렉션으로 분리하는 설계를 다시 검토해야 한다.

## 4. 화면과 메시지 흐름

로그인 후 `/home` 상단의 **서버 · 채널 열기** 또는 `/servers`로 진입한다.

```
서버 목록 | 선택한 서버의 채널 | 대화 / 입력창 | 서버 멤버
```

서버 생성 → #일반 자동 생성 → 초대 코드 발급 → 다른 사용자가 코드로 참여 → 채널에서 대화할 수 있다. 소유자는 채널을 추가할 수 있다. 기존 `/chat/{roomId}` DM 화면도 같은 `conversation.js`를 사용한다.

새 클라이언트는 REST POST 응답으로 저장 결과를 확인하고 STOMP 구독으로 실시간 메시지를 받는다. 기존 STOMP 발행 클라이언트의 `/pub/chat.send`, `/pub/ai.ask`도 유지한다. 발신자 필드를 보내더라도 무시한다.

추가한 클라이언트 처리: 한국어 IME 조합 중 Enter 방지, Shift+Enter 줄바꿈, 실패 시 입력 유지, 자동 재연결, 과거 페이지 조회, 날짜 구분, 화면에 실제 보이는 메시지만 읽음 요청, 메시지 ID 중복 표시 방지. 서버 데이터는 textContent로 렌더링한다.

**전송 보장의 한계:** ID 중복 표시 방지는 DB 중복 저장 방지가 아니다. REST 응답을 잃은 뒤 사람이 다시 전송하면 중복 저장될 수 있다. 자동 재전송은 하지 않는다. 재연결 시 최신 100개를 다시 병합하지만 장시간 오프라인 구간 전체를 보장하는 영속 replay 프로토콜은 없다. 뒤늦은 구간은 이전 내역 조회가 필요하다. DB 저장과 브로드캐스트 사이 장애에 대비한 outbox도 아직 없다.

## 5. 실행

기존 작업 파일을 먼저 커밋/보관한 후 개발 브랜치로 전환한다.

```powershell
git fetch origin
git switch --track origin/feat/discord-server-channels
# 이미 로컬 브랜치가 있으면: git switch feat/discord-server-channels
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

필수 환경 변수는 `MONGODB_URI`, `APP_AES_KEY_BASE64`, `APP_JWT_SECRET_BASE64`이다. AES와 JWT에는 서로 다른 32바이트 이상 용도에 맞는 키를 설정한다. **기존 DB를 사용하는 경우 AES 키를 새로 생성하지 말 것. 기존 암호화 데이터의 복호화에 필요하다.**

`APP_ALLOWED_ORIGINS` 기본값은 `http://localhost:8080,http://127.0.0.1:8080`이다. 다른 PC/IP/도메인, 프록시 또는 HTTPS로 접속할 경우 브라우저가 사용하는 정확한 Origin을 쉼표로 나열한다. 예: `https://chat.example.com`. 모든 Origin을 허용하는 `*`로 되돌리지 않는다.

AI는 `APP_OPENAI_ENABLED=false`가 기본이다. 사용하려면 `APP_OPENAI_ENABLED=true`와 `OPENAI_API_KEY`를 설정한다. `/ai 질문`은 최근 메시지 최대 50개 중 현재 질문을 제외한 내용을 외부 OpenAI API로 보낸다. 모든 참여자에게 이를 알리고 동의/보존/삭제 정책을 정한 뒤 켜야 한다. AI 동시 실행은 프로세스당 4개로 제한했지만 사용자별 속도 제한, 일별 비용 제한은 별도 작업이다.

## 6. 기존 DB 적용 전 확인

우선 별도의 개발 DB에서 확인한다. 이번 코드는 실제 운영 DB의 데이터나 인덱스를 자동 삭제하지 않는다. 인덱스 추가 권한이 필요하다.

새 unique 인덱스 추가 전에 아래 **조회 전용** mongosh 집계로 기존 문자열 membersKey 중복을 점검한다.

```javascript
db.rooms.aggregate([
  {$match: {membersKey: {$type: 'string'}}},
  {$group: {_id: '$membersKey', ids: {$push: '$_id'}, count: {$sum: 1}}},
  {$match: {count: {$gt: 1}}}
]);
db.rooms.getIndexes();
```

중복이 있으면 인덱스 생성이 실패하여 시작이 중단될 수 있다. 방/메시지/읽음 데이터를 함께 분석한 뒤 별도 마이그레이션해야 하며 무작정 방을 삭제하면 안 된다. 기존 `membersKey_unique` 인덱스는 자동 삭제하지 않는다. 새 그룹은 null이 아닌 키를 쓰므로 그 인덱스가 남아 있어도 새 그룹끼리 충돌하지 않는다.

기존 `alice#bob` DM은 저장된 참여자가 요청한 계정들과 **대소문자까지 정확히 일치**할 때만 재사용한다. 예전 코드가 `Alice`를 `alice`로 저장했다면 원래 소유자를 소스만으로 복구할 수 없으므로 자동 권한 변경을 하지 않는다. 기존 데이터 검토와 명시적 마이그레이션이 필요하다.

## 7. 테스트

```bash
bash ./gradlew test bootJar --no-daemon
node --test src/test/js/conversation.test.cjs
```

MongoDB 통합 테스트는 `MONGODB_TEST_URI`가 설정되어야 실행되며, 없으면 건너뛴다. 테스트용 MongoDB에서만 `MONGODB_TEST_URI=mongodb://localhost:27017`을 설정한다. 테스트는 임의 이름의 `messenger_test_*` 및 `messenger_http_test_*` DB를 만들고 삭제하며 운영 `MONGODB_URI`를 통합 테스트 주소로 사용하지 않는다.

GitHub Actions는 MongoDB 7.0 서비스, Java 21, Node 22로 단위/통합 테스트와 bootJar를 실행한다. HTTP 통합 테스트는 실제 Spring 애플리케이션을 기동해 공개 HTML, 인증 실패, 서버 생성, 비멤버 거부, 발신자 위조 무시, 옛 DM 우회 차단을 확인한다. 각 실행의 성공 여부는 Actions 결과에서 확인한다.

자동화되지 않은 확인: 실제 브라우저 두 계정 간 WebSocket 대화, 모바일 레이아웃/접근성, 프록시·TLS·운영 MongoDB, 부하·장시간 재접속 테스트. UI 및 브라우저 WebSocket 전체 E2E가 통과했다고 주장하지 않는다.

## 8. 남은 작업과 개발 순서

| 단계 | 남은 항목 | 선행 이유 |
|---|---|---|
| 운영 전 필수 | 브라우저 E2E, 레거시 데이터 감사, 가입/로그인 속도 제한, 사용자 ID 정규화 일관성, 의존성 보안 점검, 로그/모니터링 | 기존 데이터·운영 환경에서 별도 확인 필요 |
| 커뮤니티 관리 | 서버 탈퇴/강퇴/삭제, 소유권 이전, 초대 철회, 관리자 역할과 채널별 권한, 카테고리 | 현재는 소유자/멤버 두 수준뿐 |
| 대화 완성도 | 클라이언트 메시지 ID 기반 저장 멱등성, 영속 재동기화, 수정/삭제, 첨부, 검색, 답장/스레드, 멘션/알림, 안 읽은 채널 수 | 신뢰할 수 있는 일상 사용성 |
| 상태/다중 기기 | 온라인/자리비움, 입력 중 표시, 읽음 커서 통합, refresh token 회전/폐기, 기기별 세션 | 현재 로그인 토큰은 만료 전 서버 폐기되지 않음 |
| 음성/화면 | WebRTC 시그널링, STUN/TURN, 다자간 미디어 서버, 음소거/권한, 화면 공유 | STOMP 텍스트 채널만으로 음성을 구현할 수 없음 |
| 배포/확장 | 데스크톱 셸/업데이트, 분산 브로커, 공유 세션/멤버십 캐시, outbox, 백업/복구 | 현재 Spring 인메모리 브로커와 JVM 세션 맵은 단일 인스턴스용 |

추가 제한: 송신 대상마다 DB에서 사용자/방/멤버십을 재확인하므로 현재 구현은 보안을 우선한 소규모 MVP이다. 무제한 서버/초대 생성에 대한 사용자별 quota, 요청 속도 제한, DOM 가상화, 채널 멤버 목록의 실시간 갱신도 아직 없다. readBy 배열과 기존 ReadCursor 두 방식은 최종적으로 하나의 읽음 커서 모델로 통합할 필요가 있다. AuthService의 가입 입력 정규화/중복 저장, 레거시 개인정보 정리도 별도 보강 대상이다.

## 설계 참고

- Spring Framework: [STOMP token authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)
- Spring Data MongoDB: [Object mapping](https://docs.spring.io/spring-data/mongodb/reference/mongodb/mapping/mapping.html)
- MongoDB: [Unique indexes and null fields](https://www.mongodb.com/docs/v7.0/core/index-unique/)
