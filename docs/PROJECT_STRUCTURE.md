# 프로젝트 구조 및 미사용 파일 정리

2026-10-06 · 원본 `3308ca1`. 변경은 기능 브랜치에 적용하며 master 자동 병합/배포나 운영 DB 마이그레이션을 하지 않습니다.

## 계층과 의존 방향

`controller`는 HTTP/STOMP/페이지 진입과 유효성 검사, `service`는 권한·업무 흐름, `repository`는 저장/조회 경계입니다. DTO는 컨트롤러·서비스에 종속되지 않습니다. Spring에는 단 하나의 강제 패키지 구조가 없으므로 이 저장소 규모에 맞춰 중복된 api/web/voice/repo 경로를 역할별로 정리했습니다.

메시지 변경·검색은 MessageActionService/MessageActionRepository, 프로필은 UserService/UserProfileRepository, 미확인 집계는 NotificationService/UnreadMessageRepository로 나눴습니다. 기존 MessageService/ChatServerService 등의 Mongo 쿼리까지 모두 순수 도메인 모델로 바꾼 헥사고날 아키텍처 작업은 아니며, 서비스의 기존 쿼리 기반 동작은 유지합니다.

음성의 Start/Command/Candidate/View/Action은 dto.VoiceCallDtos로 옮겼습니다. 인증 DTO는 dto.auth에 한 타입씩 배치하고 나머지 요청 DTO도 controller 안에서 분리했습니다. REST 경로, 요청 필드 이름(`userName` 포함), 이벤트 형식과 페이지 별칭은 유지합니다.

**MongoDB domain 클래스의 패키지·컬렉션·필드 매핑은 변경하지 않았습니다.** 기존 `_class` 정보를 재작성하거나 사용자 문서/인덱스를 삭제하지 않습니다. 신규 가입만 전화번호를 수집하지 않으며 기존 phoneEnc와 레거시 정보, 암호화 키는 유지합니다.

## 삭제한 미사용 파일

소스/템플릿/리소스 참조 및 MVC 반환 이름을 확인했습니다. 자동 등록되는 컨트롤러/빈은 단순 import 개수만으로 판단하지 않았습니다. 아래 목록은 이동·이름 변경 파일과 별도로 기능상 사용하지 않는 파일입니다.

- `src/main/java/com/individual/messenger/api/DevHashController.java` — 현재 기능이 참조하지 않는 개발용 비밀번호 해시 실험 엔드포인트.
- `src/main/java/com/individual/messenger/api/auth/dto/RegisterRequest.java` — 실제 컨트롤러가 사용하는 요청/응답과 별개인 미사용 중복 DTO.
- `src/main/java/com/individual/messenger/api/auth/dto/RegisterResponse.java` — 실제 컨트롤러가 사용하는 요청/응답과 별개인 미사용 중복 DTO.
- `src/main/java/com/individual/messenger/config/CryptoConfig.java` — 사용 중인 AES-GCM CryptoService와 별개인 미사용 AES-CBC 예제 및 전용 설정.
- `src/main/java/com/individual/messenger/crypto/Aes256.java` — 사용 중인 AES-GCM CryptoService와 별개인 미사용 AES-CBC 예제 및 전용 설정.
- `src/main/java/com/individual/messenger/domain/ChatMessage.java` — 실제 메시지 저장/전송 모델 Message와 별개인 미사용 모델.
- `src/main/java/com/individual/messenger/rt/SseController.java` — 현재 STOMP 채팅 흐름에서 호출되지 않는 SSE 실험 엔드포인트.
- `src/main/java/com/individual/messenger/dto/AddFriendRequest.java` — 실제 컨트롤러가 사용하는 요청/응답과 별개인 미사용 중복 DTO.
- `src/main/java/com/individual/messenger/dto/CreateDmRequest.java` — 실제 컨트롤러가 사용하는 요청/응답과 별개인 미사용 중복 DTO.
- `src/main/java/com/individual/messenger/dto/CreateGroupRequest.java` — 실제 컨트롤러가 사용하는 요청/응답과 별개인 미사용 중복 DTO.
- `src/main/java/com/individual/messenger/dto/RoomDto.java` — 실제 컨트롤러가 사용하는 요청/응답과 별개인 미사용 중복 DTO.
- `src/main/resources/templates/chat.html` — 현재 페이지 컨트롤러가 반환하지 않는 이전 화면; 해당 URL은 통합 workspace 별칭으로 유지.
- `src/main/resources/templates/friends.html` — 현재 페이지 컨트롤러가 반환하지 않는 이전 화면; 해당 URL은 통합 workspace 별칭으로 유지.
- `src/main/resources/templates/home.html` — 현재 페이지 컨트롤러가 반환하지 않는 이전 화면; 해당 URL은 통합 workspace 별칭으로 유지.
- `src/main/resources/templates/rooms.html` — 현재 페이지 컨트롤러가 반환하지 않는 이전 화면; 해당 URL은 통합 workspace 별칭으로 유지.
- `src/main/resources/templates/servers.html` — 현재 페이지 컨트롤러가 반환하지 않는 이전 화면; 해당 URL은 통합 workspace 별칭으로 유지.
- `src/main/resources/templates/sse.html` — 현재 페이지 컨트롤러의 반환 경로와 실제 클라이언트에서 사용하지 않는 실험 화면.
- `src/main/resources/templates/webSocket.html` — 현재 페이지 컨트롤러의 반환 경로와 실제 클라이언트에서 사용하지 않는 실험 화면.
- `src/main/resources/templates/ws.html` — 현재 페이지 컨트롤러의 반환 경로와 실제 클라이언트에서 사용하지 않는 실험 화면.
- `src/main/resources/static/js/home.js` — 삭제한 이전 화면만 참조하고 남은 화면/모듈의 참조가 없음.
- `src/main/resources/static/css/messenger.css` — 삭제한 이전 화면만 참조하고 남은 화면/모듈의 참조가 없음.

`/api/sse` 및 개발용 해시 데모는 제거했습니다. `/api/dm`, `/api/messages`, `/api/voice`, 기존 화면 별칭은 유지합니다. 삭제한 소스는 Git 이력에서 복구할 수 있습니다.

## 의도적으로 유지한 것

OpenAiResponse는 OpenAiService가 실제로 사용합니다. DmService/ReadCursor는 기존 DM API에서 사용합니다. CryptoService는 과거 회원 정보 조회/레거시 로그인에 필요합니다. 현재 workspace 및 auth/friends/servers/conversation/realtime/voice 모듈, application.yml, Gradle Wrapper, Docker/Render/PowerShell 구성, 문서는 유지합니다. 비어 있는 favicon도 범위 밖의 동작 변화 없이 유지했습니다.

사용하지 않는 Kotlin JVM/Spring 플러그인과 Lombok 의존성·annotation processor만 제거했습니다. Kotlin DSL 빌드 파일은 Java 프로젝트에서도 사용하는 구성 파일이므로 삭제하지 않습니다. Python 테스트 캐시는 Git 제외 목록에 추가했습니다.

## 검증

기존 회귀 테스트에 회원가입 무전화번호/단일 저장/중복 충돌/필수 필드, 기존 암호화 정보 유지, 계층 참조 검사와 실제 브라우저 가입·로그인을 추가했습니다. 실행 결과는 [REFACTOR_VALIDATION.md](REFACTOR_VALIDATION.md)에 구분하여 기록합니다.

설계 참고: [Spring Boot 코드 구조 문서](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).

기존 음성 브라우저 검사에서 가상 마이크 준비가 대기하던 문제를 줄이기 위해 테스트 창 활성화와 Chromium 가상 권한 UI 설정을 명시했습니다. 실제 RTCPeerConnection/양방향 RTP 검사는 유지하며 물리 마이크나 실제 권한 팝업 UX를 검증한 것으로 표현하지 않습니다.
