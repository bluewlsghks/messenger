# RabbitMQ STOMP 목적지 호환

2026-10-06 · 실제 두 인스턴스 검사에서 RabbitMQ가 `/topic/chat/{room}/read`의 추가 슬래시를 거부하는 문제를 확인했습니다. 단일 Simple Broker 테스트만으로는 드러나지 않았습니다.

Relay 모드에서만 `BrokerDestinationCodec`이 애플리케이션의 `/topic/chat/{room}`·`/read`를 wire 목적지 `/topic/chat.{room}`·`.read`로 변환합니다. 기존 `/sub/chat/{room}` 구독 별칭은 유지됩니다. 클라이언트 요청은 **기존 인가 후 변환**, broker 수신은 **원래 논리 목적지로 복원 후 기존 outbound 재인가**합니다. 사용자 큐와 내부 registry broadcast는 변환하지 않습니다. Simple Broker의 동작도 그대로입니다.

와일드카드나 퍼센트 인코딩 입력을 새로운 허용 경로로 바꾸지 않습니다. DTO·저장 문서·REST URL은 변경하지 않습니다. 구독 ID, 명령, Principal 및 메시지 메타데이터를 보존하며 변환·읽음·역변환·비정상 입력·수신 권한 철회에 대한 Java 검사 6개를 추가했습니다. 실제 실행 결과는 ROADMAP_VALIDATION.md에 기록합니다.

유료 브로커를 생성하거나 외부 네트워크 정책을 완화한 것이 아닙니다. 선택적인 로컬 RabbitMQ와 기존 무료 실행 모드를 유지합니다.

참고: [RabbitMQ STOMP destinations](https://www.rabbitmq.com/docs/stomp#destinations), [Spring MessageBrokerRegistry](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/config/MessageBrokerRegistry.html).
