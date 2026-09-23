# Messenger · 실시간 메신저

Java 21 / Spring Boot / MongoDB 기반 개인 프로젝트입니다. 기존 DM·그룹 대화에 **Discord형 서버 → 텍스트 채널 구조**를 추가한 개발 브랜치입니다.

> 이번 버전은 텍스트 서버 MVP입니다. Discord 전체 기능이나 음성/화면 공유, 데스크톱 앱을 구현한 것은 아닙니다.

## 화면

로그인 후 홈 상단 **서버 · 채널 열기** 또는 `/servers`로 진입합니다.

- 왼쪽 서버 목록, 채널 목록, 대화 영역, 멤버 목록
- 서버 생성, 기본 #일반 채널, 소유자의 채널 추가
- 7일 만료 초대 코드, 초대받은 사용자의 참여
- 기존 DM/친구 기능 및 공통 채팅 UI
- 메시지 저장/조회, 날짜 구분, 커서 페이지 조회, 읽음 표시
- 재연결, 한국어 IME 처리, 전송 실패 시 입력 유지

## 구조

`api` → `ChatMessagingService` / `ChatServerService` → MongoDB.
REST와 STOMP가 공통 인가 및 전송 로직을 사용합니다. 화면은 Thymeleaf + JavaScript입니다.

STOMP CONNECT에 Bearer JWT를 보내며 방/채널의 메시지 구독, 조회, 전송에 멤버십을 확인합니다. 발신자 ID와 표시 이름은 서버가 로그인 사용자 정보로 정합니다.

## 실행

필수 환경 변수:

| 변수 | 용도 |
|---|---|
| `MONGODB_URI` | 개발 MongoDB 연결 |
| `APP_AES_KEY_BASE64` | 기존 개인정보 암호화 키. 기존 DB 사용 시 유지 |
| `APP_JWT_SECRET_BASE64` | JWT 서명 키 |
| `APP_ALLOWED_ORIGINS` | 허용 Origin. 기본 localhost/127.0.0.1:8080 |
| `APP_OPENAI_ENABLED` | 기본 false. 선택적 AI 사용 여부 |
| `OPENAI_API_KEY` | AI 활성화 시에만 필요 |

```powershell
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

기본 접속 주소: `http://localhost:8080`. AI 키 없이 일반 메신저를 실행할 수 있습니다.

**AI 주의:** 활성화하면 `/ai 질문`이 최근 대화 일부를 외부 OpenAI API로 전송합니다. 모든 참여자에게 이를 안내한 후 사용해야 합니다.

## 주요 API

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | `/api/auth/login`, `/api/auth/register` | 인증 |
| GET / POST | `/api/servers` | 참여 서버 목록 / 서버 생성 |
| GET | `/api/servers/{id}` | 서버/채널/멤버 조회 |
| POST | `/api/servers/{id}/channels` | 소유자의 채널 생성 |
| POST | `/api/servers/{id}/invites` | 소유자의 초대 코드 발급 |
| POST | `/api/servers/join` | 초대 코드로 참여 |
| GET | `/api/rooms`, `/api/rooms/my` | 내 DM/그룹 목록 |
| POST | `/api/rooms/dm`, `/api/rooms/group` | DM/그룹 생성 |
| POST | `/api/messages` | 저장 및 실시간 전송 |
| GET | `/api/messages/{roomId}?before=...&beforeId=...&limit=100` | 과거 내역 |
| POST | `/api/messages/read` | 읽음 처리 |

WebSocket: `/ws-stomp` (SockJS), 발행 `/pub/chat.send`, `/pub/ai.ask`, 구독 `/sub/chat/{roomId}`, `/sub/chat/{roomId}/read`, 개인 오류 `/user/queue/errors`.
옛 `/api/dm` 경로도 유지하지만 `me` 요청값은 신뢰하지 않습니다.

## 검증 및 제한

JUnit/Mockito 단위 테스트, 실제 MongoDB 통합 테스트, HTTP 애플리케이션 기동 테스트, Node 입력/정렬 테스트, GitHub Actions를 포함합니다. MongoDB 통합 테스트는 테스트용 `MONGODB_TEST_URI`가 없으면 건너뜁니다.

기존 DB에서는 문자열 `membersKey` 중복과 과거 ID 대소문자 변경을 먼저 점검해야 합니다. 인덱스가 새로 추가되며, 데이터와 기존 인덱스를 자동 삭제하지 않습니다.

실제 브라우저/운영 환경 검증, 메시지 저장 멱등성·영속 재동기화, 세부 역할, 음성/화면 공유, 첨부, 검색, 푸시, 다중 인스턴스 등은 후속 작업입니다.

**[프로젝트 구조 분석, 변경 내역, 실행/마이그레이션 주의사항, 남은 개발 과제](docs/DISCORD_MVP.md)**
