package com.individual.messenger.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import static org.junit.jupiter.api.Assertions.*;

class BrokerDestinationLifecycleTest {
    @Test void relayCanAssignSystemSessionAfterEncodingServerPublication() {
        for (String destination : new String[]{"/topic/chat/room", "/queue/events-usersession"}) {
            var originalHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            originalHeaders.setDestination(destination);
            Message<?> original = MessageBuilder.createMessage(new byte[0], originalHeaders.getMessageHeaders());
            Message<?> encoded = new BrokerDestinationCodec(false).preSend(original, null);
            var actual = MessageHeaderAccessor.getAccessor(encoded, StompHeaderAccessor.class);
            assertNotNull(actual);
            assertEquals(StompCommand.SEND, actual.getCommand());
            assertNull(actual.getSessionId());
            assertTrue(actual.isMutable());
            assertDoesNotThrow(() -> actual.setSessionId("_system_"));
            assertNull(SimpMessageHeaderAccessor.wrap(original).getSessionId());
            actual.setImmutable();
            assertFalse(actual.isMutable());
        }
    }
    @Test void outboundFrameworkCanRestoreOriginalUserDestinationOnItsCopy() {
        var headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headers.setSessionId("session");
        headers.setDestination("/exchange/amq.direct/events-usersession");
        headers.setNativeHeader("simpOrigDestination", "/user/queue/events");
        Message<?> original = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        Message<?> decoded = new BrokerDestinationCodec(true).preSend(original, null);
        var actual = MessageHeaderAccessor.getAccessor(decoded, StompHeaderAccessor.class);
        assertNotNull(actual);
        assertEquals("/queue/events-usersession", actual.getDestination());
        assertDoesNotThrow(() -> actual.setDestination(actual.getFirstNativeHeader("simpOrigDestination")));
        assertEquals("/user/queue/events", actual.getDestination());
        assertEquals("/exchange/amq.direct/events-usersession", StompHeaderAccessor.wrap(original).getDestination());
    }
}
