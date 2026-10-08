# 메시지 반응·고정·개인 북마크와 변경 커서 동기화

2026-10-08 · 기준 master `6f4302253b72fe0b660603f6d1c522a2e3c3c308`.
브랜치 `feat/message-collaboration-sync`. 기존 README의 경력·연락처·포트폴리오 구조는 유지합니다.
이 문서는 코드 설계와 확인한 검증 결과를 구분합니다. master 자동 병합, 실제 배포, 유료 서비스 활성화, 운영 DB 삭제/재작성은 하지 않습니다.

## 사용 방법과 권한

메시지 아래 **반응**에서 👍 ❤️ 😄 🎉 🙏 👀 🚀를 선택합니다. 같은 반응을 다시 누르면 취소합니다. 개수와 반응한 사람을 확인할 수 있습니다. 반응 작성은 현재 방 쓰기 권한이 필요하고, 한 반응에는 최대 1,000명까지 기록합니다.

**📌 고정**은 방 공용입니다. DM·그룹은 쓰기 가능한 참여자, 서버 채널은 쓰기 가능한 소유자·관리자만 변경합니다. **☆ 북마크 저장**은 나만 보는 저장 상태입니다. 대화 위의 **고정 메시지 / 내 북마크** 버튼에서 페이지별로 조회합니다. 북마크는 현재 대화별 목록이며, 모든 대화를 한 번에 조회하는 전역 북마크함은 아닙니다.

원본 메시지 삭제 시 반응·고정 메타데이터를 같은 쓰기에서 지웁니다. 북마크는 원본 참조만 저장하고 조회에서 삭제된 메시지를 제외합니다. 남은 북마크 참조의 자동 정리는 이번 범위가 아닙니다. 권한 상실 후 새 조회·변경은 거부하고, 열린 화면도 권한 재확인 실패 시 비웁니다. 이미 사용자에게 전달된 내용을 소급하여 회수하는 보장은 아닙니다.

## API

모든 경로의 시작은 `/api/messages/{roomId}`이며, 기존 JWT·세션·요청 예산 필터를 그대로 통과합니다. actor/owner는 인증 정보로 결정합니다.

| 메서드·경로 | 내용 |
|---|---|
| `GET /collaboration` | 현재 사용자의 반응·고정 가능 여부 |
| `PUT /{messageId}/reactions/{key}` | 원하는 반응을 추가, 중복 요청은 추가하지 않음 |
| `DELETE /{messageId}/reactions/{key}` | 본인 반응 취소, 없는 반응은 그대로 |
| `PUT/DELETE /{messageId}/pin` | 고정 설정/해제 |
| `PUT/DELETE /{messageId}/bookmark` | 개인 저장/해제, 성공 204 |
| `GET /pins?after=…&limit=50` | 공용 고정 메시지 페이지 |
| `GET /bookmarks?after=…&limit=50` | 인증된 본인의 개인 저장 메시지 페이지 |
| `POST /bookmark-status` | 최대 1,000개 메시지 ID 배열에 대한 본인 저장 상태 |
| `POST /snapshots` | 최대 100개 ID 배열의 현재 메시지 상태, 읽음 정보 재대조 |
| `GET /changes?cursor=…&limit=100` | 변경 기록 커서 기반 현재 상태 복구 |

반응 키는 `like`, `heart`, `laugh`, `party`, `thanks`, `eyes`, `rocket`만 허용합니다. MongoDB 경로에 임의 이모지·입력 문자열을 삽입하지 않습니다. 메시지 ID는 소문자 ObjectId 문자열만 받습니다. 목록은 최대 100개이며, 북마크가 가리키는 원본이 삭제되어 빈 페이지가 나와도 `hasMore`와 `next`에 따라 다음 페이지를 조회해야 합니다. 페이지는 시점 고정 스냅샷이 아니므로 동시 고정 변경은 목록을 다시 열어 확인합니다.

## 저장 정합성

`messages`의 `reactions`·`pinned`·`pinnedBy`·`pinnedAt`은 추가 필드입니다. 기존 문서·컬렉션·Java 도메인 패키지·`_class`를 옮기지 않습니다. 반응 변경은 `$addToSet`/`$pull`과 버전 증가 및 `publicationPending` 설정을 하나의 조건부 쓰기로 수행합니다. 명시적 PUT/DELETE이므로 네트워크 재시도로 상태가 두 번 뒤집히지 않습니다. 반응·고정도 메시지 버전을 증가시키므로 과거 버전의 본문 수정/삭제는 기존 409 충돌 처리 대상입니다.

`message_bookmarks`에는 owner·roomId·messageId 참조만 저장합니다. 본문을 복제하거나 공용 Message/STOMP 이벤트에 개인 북마크 정보를 포함하지 않습니다. owner+room+message 고유 인덱스를 적용합니다.

## 전체 순회에서 변경 커서로

기존 `conversation.js`는 방 입장·재연결 시 `/sync`를 100개씩 끝까지 조회했습니다. 새 UI는 `/changes`만 사용하고, 기존 `/sync` API는 구 클라이언트 호환용으로 유지합니다. `ROADMAP_IMPLEMENTATION.md`의 전체 순회 설명은 이전 구현 기록이며 현재 UI는 이 문서를 우선합니다.

`message_change_journals`는 **방당 하나의 문서**에 epoch, sequence, 최근 1,000개 messageId 배열을 저장합니다. `$inc sequence`와 `$push ids/$slice -1000`이 같은 원자적 쓰기입니다. 배열 길이와 최종 순번으로 각 항목의 순번을 계산하므로, 순번을 먼저 발급하고 나중에 별도 문서에 기록할 때 생기는 누락 구간을 만들지 않습니다. 같은 메시지의 재발행은 중복 항목으로 남을 수 있으며, 커서는 중복 제거 후 응답 개수가 아니라 실제 스캔한 기록 수만큼 진행합니다.

변경 기록은 `MessageProjection`으로 outbox에 참여합니다. 원본 메시지의 pending 상태와 원본 내용은 함께 저장되지만 저널·브로커·검색은 별도의 후속 작업입니다. 저널 실패는 outbox 완료를 막고 재시도합니다. 브로커 중단·재시도 지연 중에는 저널 반영도 늦을 수 있습니다. API 응답은 현재 메시지를 원본 DB에서 다시 읽으며, 라이브 이벤트만으로 동기화 커서를 앞당기지 않습니다.

최초 접속/epoch 불일치/보관 범위 초과/미래 순번이면 `reset=true`와 최신 100개를 반환합니다. **저널 기준점을 먼저 잡고** 이력을 읽어, 그 사이 변경을 다음 요청에서 다시 확인할 수 있게 합니다. 브라우저는 오래된 캐시를 교체합니다. 유효한 커서에서는 변경 ID를 최대 100개 스캔하고, 중복을 제거한 현재 메시지(삭제 표시 포함)를 반환합니다. 변경이 없으면 메시지 본문 조회가 필요하지 않습니다.

현재 방이 보이는 동안 10초 주기로 보완 동기화하며, 한 번에는 최대 10페이지로 제한합니다. 재연결·포커스 복귀 시에도 동기화합니다. 커서는 해당 메모리 캐시와 수명을 같이하며 별도로 localStorage에 영구 저장하지 않습니다. 읽음 상태는 기존 readBy 모델을 유지하고 재연결 시 최대 1,000개 보유 ID만 100개씩 대조합니다. 이 처리는 총 이력 수와 무관한 상한을 갖습니다. 즉시 알림은 기존 STOMP 경로가 담당합니다.

일반적인 primary 읽기를 전제로 하며 저널 조회는 명시적으로 primary입니다. 단일 문서 단위의 원자성이지 분산 트랜잭잭션·정확히 한 번 전달·모든 편집 중간 이력 보존은 아닙니다. 하나의 매우 활발한 방은 단일 저널 문서에 경합할 수 있으므로 대규모 확장 전에는 실측이 필요합니다.

## 검증 상태

- 로컬: 전체 브라우저 JS 구문 검사 성공, Node 테스트 **57개 통과**(기존 48+신규 9). Python 브라우저 스크립트 구문 검사 성공.
- 신규 Java/MongoDB 테스트 **18개 작성**: 동시 반응·중복·취소·인원 상한, 원본 삭제, 관리자/읽기 전용 채널, 북마크 비공개·삭제 페이지·권한 철회, 원자적 저널·만료·수정/삭제·초기 경계·outbox 실패, 읽음 복구 등.
- 신규 Playwright 스크립트는 실제 HTTP/STOMP 기반 두 브라우저의 반응·고정·개인 저장·오프라인 편집 복구·삭제를 검사하도록 CI에 연결합니다.
- 이 로컬 런타임에서는 Gradle 다운로드 호스트에 연결하지 못했습니다. Java 빌드·MongoDB·Playwright 실행은 아직 확인 전이며 PR CI 결과로 갱신합니다. 이전 커밋의 통과 기록을 이번 변경의 통과 결과로 대체하지 않습니다.
- 성능 수치·대규모 부하·실기기 검증은 아직 수행하지 않았습니다. 요청 수와 지연이 개선되었다는 실측 수치는 제시하지 않습니다.

## 참고

MongoDB [single-document atomicity](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/), [$push](https://www.mongodb.com/docs/manual/reference/operator/update/push/), [$slice](https://www.mongodb.com/docs/manual/reference/operator/update/slice/), Spring Data [MongoTemplate CRUD](https://docs.spring.io/spring-data/mongodb/reference/mongodb/template-crud-operations.html).
