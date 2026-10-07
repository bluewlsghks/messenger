package com.individual.messenger.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.ImmutableMessageChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BrokerDestinationLifecycleTest {
    private Message<?> publication(String destination) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        headers.setNativeHeader("chat-recipient", "alice");
        headers.setNativeHeader("chat-room", "room");
        return MessageBuilder.createMessage(new byte[]{1, 2}, headers.getMessageHeaders());
    }

    @Test void serverPublicationRetainsSimpAccessorAfterEncodingAndFreezing() {
        for (String destination : new String[]{"/topic/chat/room", "/topic/chat/room/read", "/queue/events-usersession"}) {
            Message<?> original = publication(destination);
            Message<?> encoded = new BrokerDestinationCodec(false).preSend(original, null);
            encoded = new ImmutableMessageChannelInterceptor().preSend(encoded, null);
            var actual = MessageHeaderAccessor.getAccessor(encoded, MessageHeaderAccessor.class);
            assertNotNull(actual);
            assertEquals(SimpMessageHeaderAccessor.class, actual.getClass());
            assertFalse(actual.isMutable());
            assertNull(SimpMessageHeaderAccessor.getSessionId(encoded.getHeaders()));
            assertEquals("alice", SimpMessageHeaderAccessor.wrap(encoded).getFirstNativeHeader("chat-recipient"));
            assertSame(original.getPayload(), encoded.getPayload());
            // The real relay takes this branch for SIMP, not an immutable STOMP accessor.
            var relayHeaders = StompHeaderAccessor.wrap(encoded);
            assertEquals(StompCommand.SEND, relayHeaders.updateStompCommandAsClientMessage());
            assertDoesNotThrow(() -> relayHeaders.setSessionId(StompBrokerRelayMessageHandler.SYSTEM_SESSION_ID));
            assertNull(SimpMessageHeaderAccessor.getSessionId(original.getHeaders()));
            assertEquals(destination, SimpMessageHeaderAccessor.getDestination(original.getHeaders()));
        }
    }

    @Test void finalizedBrokerChannelMessagesReachRealRelayHeaderHandling() {
        var channel = new ExecutorSubscribableChannel(); // synchronous execution; no external TCP server
        var relay = new HeaderOnlyRelay();
        channel.addInterceptor(new BrokerDestinationCodec(false));
        channel.addInterceptor(new ImmutableMessageChannelInterceptor());
        channel.subscribe(relay::receive);
        for (String path : new String[]{"/topic/chat/room", "/topic/chat/room/read",
                "/queue/events-usersession", "/queue/errors-usersession"}) {
            assertTrue(channel.send(publication(path)));
        }
        assertEquals(4, relay.received.get());
    }

    @Test void clientSubscriptionKeepsItsSessionAcrossChannelFinalization() {
        var h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        h.setSessionId("browser-session"); h.setSubscriptionId("events-1");
        h.setDestination("/queue/events-userbrowser-session");
        Message<?> encoded = new BrokerDestinationCodec(false).preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null);
        encoded = new ImmutableMessageChannelInterceptor().preSend(encoded, null);
        var actual = MessageHeaderAccessor.getAccessor(encoded, StompHeaderAccessor.class);
        assertNotNull(actual);
        assertFalse(actual.isMutable());
        assertEquals(StompCommand.SUBSCRIBE, actual.getCommand());
        assertEquals("browser-session", actual.getSessionId());
        assertEquals("events-1", actual.getSubscriptionId());
        assertEquals("true", actual.getFirstNativeHeader("exclusive"));
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

    /** Only header processing is under test; real broker delivery is tested in infrastructure.py. */
    private static final class HeaderOnlyRelay extends StompBrokerRelayMessageHandler {
        private final AtomicInteger received = new AtomicInteger();
        HeaderOnlyRelay() {
            super(new ExecutorSubscribableChannel(), new ExecutorSubscribableChannel(),
                    new ExecutorSubscribableChannel(), List.of("/topic", "/exchange"));
        }
        @Override public boolean isBrokerAvailable() { return true; }
        void receive(Message<?> message) {
            // No start/TCP connection: exercise the real immutable-header branch,
            // then the handler safely returns because it has no active connection.
            super.handleMessageInternal(message);
            received.incrementAndGet();
        }
    }
}
