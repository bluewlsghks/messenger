# 선택적 외부 기능 및 운영 도구

기본 실행에는 기존 **Java 21 + 개발 MongoDB + AES/JWT 키**만 필요합니다. 기존 데이터·키는 유지합니다. 이 문서는 설치/실행을 대신하지 않으며, 유료 서비스나 고정 도메인을 자동 생성하지 않습니다. 실제 검증 결과는 ROADMAP_VALIDATION.md를 확인합니다.

## 기본 실행

브랜치 갱신 후 Gradle 동기화, `MessengerApplication` Run/Debug를 사용합니다. Boot 4 전환으로 직접 Spring Mongo 설정을 쓰는 실행 구성은 `spring.data.mongodb.*` 대신 `spring.mongodb.*`로 바꿉니다. 권장 환경 변수 `MONGODB_URI`는 이름이 같아 그대로 사용합니다. Java 21·Gradle Wrapper·main 클래스 위치는 유지했습니다.

`APP_PUBLIC_TUNNEL_ENABLED=true`도 유지할 수 있습니다. 비밀값을 Git에 넣지 마세요. 운영 키를 교체하면 과거 암호화 값이 읽히지 않을 수 있으므로 AES 키는 특히 보존합니다. 브라우저는 새 버전 적용 후 다시 로그인하는 편이 세션/refresh 기능 확인에 좋습니다.

## RabbitMQ / Elasticsearch (선택)

Docker를 이미 설치한 개발 PC에서만 아래를 직접 실행합니다. `deploy/compose.dev.yml`의 서비스는 모두 loopback으로만 포트를 게시합니다. Mongo/Elasticsearch의 인증 없는 설정은 **로컬 테스트 전용**입니다. 외부 인터페이스에 게시하지 마세요.

```powershell
# 임의의 서로 다른 실제 로컬 값을 환경변수 편집기에 설정합니다. 아래는 자리표시자입니다.
$env:APP_BROKER_RELAY_LOGIN = '<로컬 브로커 계정>'
$env:APP_BROKER_RELAY_PASSWORD = '<충분히 긴 랜덤 비밀번호>'
docker compose -f deploy/compose.dev.yml --profile broker --profile search up -d
```

Compose는 profile 사용 여부와 관계없이 변수 치환을 먼저 수행하므로 브로커 변수 두 개는 설정합니다. 기존 Mongo를 계속 쓰는 경우 `database` profile은 켜지 않습니다. 브로커/검색과 메신저는 별도 프로세스입니다. Run 버튼만으로 Docker까지 생성하지 않습니다.

두 Messenger 인스턴스에는 **같은 MongoDB/AES/JWT 키와 broker 설정**, 서로 다른 `SERVER_PORT`를 사용합니다. 각각 실제 Origin을 정확히 허용합니다.

```text
APP_BROKER_RELAY_ENABLED=true
APP_BROKER_RELAY_HOST=127.0.0.1
APP_BROKER_RELAY_PORT=61613
APP_BROKER_RELAY_LOGIN=<동일 계정>
APP_BROKER_RELAY_PASSWORD=<동일 비밀번호>
APP_SEARCH_ENABLED=true
APP_SEARCH_URL=http://127.0.0.1:9200
APP_SEARCH_INDEX=messenger-messages-v1
```

원격 Elasticsearch는 HTTPS와 최소 권한 API key(`APP_SEARCH_API_KEY`)를 사용합니다. 신규 인덱스는 설정 JSON으로 생성되고 새 메시지는 비동기로 반영됩니다. 기존 데이터 재색인은 별도 운영자 인증의 `POST /api/operations/search/reindex`, 진행 확인은 `GET /api/operations/search`입니다. 데이터 index/분석기를 변경할 때는 새 index 이름을 선택하고 재색인 후 전환합니다.

## Web Push (선택)

VAPID 키는 서버 운영자가 한 번 생성하여 환경변수로 관리합니다. `scripts/ops/GenerateVapidKeys.java`는 Java 21 단일 소스 실행으로 키를 **새 파일에만** 저장하며 이미 있는 파일은 덮어쓰지 않습니다.

```text
java scripts/ops/GenerateVapidKeys.java .local/push.env
```

`.local/push.env`의 공개/비밀 키를 IntelliJ 환경변수에 추가하고, 자신의 실제 연락용 subject를 설정합니다. 파일을 업로드/공유하지 마세요. 서버 여러 개는 같은 VAPID 키를 사용합니다.

```text
APP_PUSH_ENABLED=true
APP_PUSH_PUBLIC_KEY=<공개 키>
APP_PUSH_PRIVATE_KEY=<비밀 키>
APP_PUSH_SUBJECT=mailto:<실제 운영자 이메일>
```

사용자는 HTTPS 페이지에서 알림 패널의 **브라우저를 닫아도 알림 받기**를 직접 눌러 권한을 허용합니다. 클라이언트 설치/사용자 동의 없는 강제 구독은 하지 않습니다. 새 세션으로 로그인한 기기는 필요 시 재구독합니다. 이 브라우저의 대화 음소거 설정도 서버 Push 구독에 반영됩니다. 서버/브라우저 Push 제공자의 네트워크 연결이 필요하며 서비스워커는 대화 본문/토큰을 캐시하지 않습니다. 알림 본문은 일반적인 새 메시지/통화 안내뿐입니다.

탭 종료 후 수신은 브라우저/OS 정책에 영향을 받습니다. OS가 브라우저 백그라운드 동작까지 차단하면 알림도 늦거나 오지 않을 수 있습니다. iOS 등 설치 조건·백그라운드 정책은 실제 대상 기기에서 확인해야 합니다. URL이 바뀌는 Quick Tunnel은 Origin이 바뀌므로 구독/사이트 권한도 다시 설정해야 합니다.

## TURN / 실기기 음성

`deploy/turn/turnserver.conf.example`은 별도 TURN 호스트를 위한 보안 설정 시작점입니다. 실제 NAT·IP·인증서·방화벽을 넣고 coturn에 적용해야 합니다. Docker/설정 파일을 작성했다고 외부에서 접속 가능한 TURN을 만든 것은 아닙니다.

```text
VOICE_TURN_URLS=turn:<실제 TURN 호스트>:3478?transport=udp,turns:<실제 TURN 호스트>:5349?transport=tcp
VOICE_TURN_SECRET=<coturn의 동일 공유 비밀값>
VOICE_RELAY_ONLY=true
```

`VOICE_RELAY_ONLY` 검증 시 브라우저 WebRTC 통계의 선택된 후보가 relay인지 확인합니다. 집 Wi-Fi↔모바일망, 회사망↔집, 양방향 RTP·물리 음성, 새로고침/네트워크 변경·ICE restart, iOS/Android 화면 잠금/복귀, 실제 카메라·화면 선택 창/중지, 마이크 표시 종료를 검사합니다. 회사 방화벽의 모든 조건을 우회한다고 보장하지 않습니다.

## 모니터링 / 읽기 전용 데이터 점검

`GET /actuator/health`는 최소 UP/DOWN만 제공합니다. 상세 metrics와 `/api/operations/**`는 일반 로그인과 분리된 운영자 토큰이 필요합니다. `APP_OPERATIONS_TOKEN`을 32바이트 이상 랜덤 값으로 설정하고, 요청에는 `X-Operations-Token` 헤더를 넣습니다. 빈 설정이면 전부 거부합니다. 공개 URL의 쿼리 문자열이나 README에 토큰을 넣지 마세요.

`GET /api/operations/audit`는 중복 ID 그룹, 레거시 평문 필드의 **건수**, 고유 인덱스 유무, pending outbox, 첨부 용량을 반환합니다. 실제 ID·비밀번호·전화번호·본문은 반환하지 않습니다. ID/인덱스 충돌로 앱 시작이 거부되면 백업을 확보한 후 DB 관리자와 충돌을 정비합니다. 충돌 문서를 자동 삭제하는 스크립트는 제공하지 않습니다.

## 백업 / 안전한 복구

MongoDB Database Tools(`mongodump`, `mongorestore`)와 `mongosh`가 PATH에 있어야 합니다. **독립형 Mongo의 시점 일관성을 위해 메신저와 모든 writer를 먼저 중지**합니다. 도구는 프로세스를 임의로 종료하지 않습니다. AES 키는 DB 백업과 분리하여 안전하게 보존해야 합니다.

```text
python scripts/ops/mongo_backup.py backup --source messenger_dev --archive .local/backups/dev.archive.gz --writers-stopped
python scripts/ops/mongo_backup.py restore --source messenger_dev --target messenger_restore_check --confirm-new-database messenger_restore_check --archive .local/backups/dev.archive.gz --writers-stopped
```

연결은 `MONGODB_URI`에서 읽습니다. 복구는 다른 이름의 **새 DB만**, 기존 collection이 하나라도 있으면 거부합니다. 기존 DB 삭제/`--drop`/동일 DB 덮어쓰기는 없습니다. 다른 writer가 그 새 DB를 동시에 만들지 않게 관리자가 독점해야 합니다. 도중 실패한 복구 DB는 부분 데이터일 수 있으므로 성공으로 사용하지 말고 다른 새 DB에 다시 시도합니다.

인증 세션/일시 미디어/Push 구독/예산/재색인 작업은 백업·복구에서 제외합니다. 복원 후 사용자·방·메시지·파일 샘플과 건수를 검증하고, 로그인/키/인덱스·검색 재색인을 확인한 뒤 앱의 DB를 전환합니다. 백업은 암호화되지 않은 archive이므로 OS 암호화/접근 제어·별도 보관을 적용합니다. 원격 연결은 `--allow-remote`를 추가해야 하며 이 문서의 예시만으로 실제 운영 DB에 실행하지 않습니다.

## 의존성 검사 / 짧은 부하·재연결 검사

```text
./gradlew dependencyCoordinates
python scripts/ops/security_audit.py build/security/dependencies.json
python scripts/validate/soak.py
```

Windows에서는 `gradlew.bat`를 사용합니다. OSV에는 정확한 public Maven 좌표/버전만 전송합니다. 결과 0=완료·발견 없음, 2=발견 있음·검토 필요, 1=검사 미완료입니다. 취약점 발견을 무조건 false positive로 무시하거나 검사 실패를 성공으로 표시하지 않습니다. CI 보고서를 검토합니다.

soak는 localhost 앱과 Playwright가 필요하며 기본 60초·두 브라우저·재시도·재연결을 확인합니다. `MESSENGER_SOAK_SECONDS`는 10~1800, `MESSENGER_E2E_URL`은 loopback 주소만 받습니다. 60초 결과를 24시간 안정성·수천 동접 성능으로 표현하지 않습니다. 장기 부하/운영 DB 복구·OS 알림·Windows IntelliJ·Cloudflare의 실제 외부 접속은 별도 검증 항목입니다.

참고: [Spring Boot JSON](https://docs.spring.io/spring-boot/reference/features/json.html), [RabbitMQ STOMP](https://www.rabbitmq.com/docs/stomp), [Elasticsearch 버전 관리](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-index-2), [OSV querybatch](https://google.github.io/osv.dev/api/#tag/api/operation/OSVQueryBatch), [MongoDB Database Tools](https://www.mongodb.com/docs/database-tools/).
