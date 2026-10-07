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

2026-10-07 후속 수정: 실제 broker channel에는 codec 뒤에서 헤더를 동결하는 `ImmutableMessageChannelInterceptor`가 있습니다. `setLeaveMutable(true)`만으로 이를 피할 수 없으므로 앞선 단독 codec 테스트는 실제 채널 경계를 충분히 재현하지 못했습니다.

서버 발행은 목적지만 변환하고 **SIMP accessor를 그대로 유지**합니다. relay가 이 메시지를 받아 새 STOMP accessor를 만든 뒤 시스템 세션을 설정하게 했습니다. 이미 인증된 클라이언트 구독/수신 프레임은 STOMP 명령·세션·구독 ID와 개인 큐의 exclusive/auto-delete 정책을 유지합니다. framework의 불변성 인터셉터를 제거하거나 권한 검사를 우회하지 않습니다.

회귀 검사는 실제 `ExecutorSubscribableChannel → codec → ImmutableMessageChannelInterceptor → StompBrokerRelayMessageHandler`의 헤더 처리 경계를 통과합니다. 해당 단위 검사는 TCP delivery를 가장하지 않으며, 실제 두 앱/RabbitMQ 전송 검사는 별도의 infrastructure 시나리오에서 실행합니다. 실행 결과는 [ROADMAP_VALIDATION.md](ROADMAP_VALIDATION.md)를 참고합니다.
