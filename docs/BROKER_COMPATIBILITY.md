# RabbitMQ STOMP 목적지 호환

2026-10-06 · 실제 두 인스턴스 검사에서 RabbitMQ가 `/topic/chat/{room}/read`의 추가 슬래시를 거부하는 문제를 확인했습니다. 단일 Simple Broker 테스트만으로는 드러나지 않았습니다.

Relay 모드에서만 `BrokerDestinationCodec`이 애플리케이션의 `/topic/chat/{room}`·`/read`를 wire 목적지 `/topic/chat.{room}`·`.read`로 변환합니다. 기존 `/sub/chat/{room}` 구독 별칭은 유지됩니다. 클라이언트 요청은 **기존 인가 후 변환**, broker 수신은 **원래 논리 목적지로 복원 후 기존 outbound 재인가**합니다. 사용자 큐는 아래의 direct exchange 방식으로 변환하며, 내부 registry broadcast는 변환하지 않습니다. Simple Broker의 동작도 그대로입니다.

와일드카드나 퍼센트 인코딩 입력을 새로운 허용 경로로 바꾸지 않습니다. DTO·저장 문서·REST URL은 변경하지 않습니다. 구독 ID, 명령, Principal 및 메시지 메타데이터를 보존하며 변환·읽음·역변환·비정상 입력·수신 권한 철회에 대한 Java 검사 12개를 추가했습니다. 실제 실행 결과는 ROADMAP_VALIDATION.md에 기록합니다.

유료 브로커를 생성하거나 외부 네트워크 정책을 완화한 것이 아닙니다. 선택적인 로컬 RabbitMQ와 기존 무료 실행 모드를 유지합니다.

참고: [RabbitMQ STOMP destinations](https://www.rabbitmq.com/docs/stomp#destinations), [Spring MessageBrokerRegistry](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/config/MessageBrokerRegistry.html).

## 개인 큐의 연결 소유권

추가 실브로커 검사에서 `/queue/events-user{session}`을 exclusive로 구독한 후 시스템 연결이 같은 queue에 SEND하면 RabbitMQ의 재선언 과정에서 `RESOURCE_LOCKED`가 발생했습니다. exclusive를 해제해 공유 큐로 만드는 대신, relay의 실제 wire에서만 `/exchange/amq.direct/events-user{session}` 또는 `errors-user{session}`로 바꿉니다. 구독은 전용 auto-delete 큐를 만들고 시스템 발행은 해당 routing key로 보내므로 다른 연결이 큐를 재선언하지 않습니다.

클라이언트의 `/user/queue/events`·`errors`와 서비스의 `convertAndSendToUser` API는 그대로입니다. Spring이 세션별 목적지를 해석한 이후 broker channel에서 변환하며, 수신할 때 논리 큐 이름을 복원하고 recipient·현재 방 권한을 다시 검사합니다. 사용자 지정 queue 이름과 dead-letter 라우팅 헤더는 개인 구독에서 제거합니다. 이 변경은 로그인·방 권한을 완화하지 않으며, 종료된 브라우저의 영속 큐 보관을 보장하지 않습니다. 오프라인 복원은 DB `/sync` 경로가 담당합니다.

참고: [Spring user destinations / external broker queues](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html).

## 프레임 헤더 수명주기

시스템 발행은 처음에 `simpSessionId`가 없을 수 있습니다. 변환한 STOMP 헤더를 너무 일찍 불변으로 만들면 relay가 시스템 세션을 지정할 때 `Already immutable`로 실패합니다. codec은 원본 메시지를 바꾸지 않은 별도 헤더 사본을 다음 Spring 처리 단계까지 mutable로 유지합니다. 세션 없는 서버 발행과 개인 목적지 복원 단계의 회귀 검사를 추가했으며 최종 동결은 프레임워크 경계에서 수행합니다.
