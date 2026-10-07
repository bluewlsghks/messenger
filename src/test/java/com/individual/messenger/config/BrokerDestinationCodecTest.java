package com.individual.messenger.config;

import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.security.JwtUtil;
import com.individual.messenger.security.StompSecurityInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrokerDestinationCodecTest {
    private final BrokerDestinationCodec encode = new BrokerDestinationCodec(false);
    private final BrokerDestinationCodec decode = new BrokerDestinationCodec(true);
    private Message<?> applicationMessage(String destination) {
        var h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        h.setDestination(destination); h.setSessionId("test-session");
        h.setHeader("chatRecipient", "alice");
        return MessageBuilder.createMessage(new byte[]{1, 2}, h.getMessageHeaders());
    }
    private String destination(Message<?> message) { return StompHeaderAccessor.wrap(message).getDestination(); }

    @Test void applicationPublicationUsesOneRabbitTopicName() {
        Message<?> original = applicationMessage("/topic/chat/room_12");
        Message<?> wire = encode.preSend(original, null);
        assertEquals("/topic/chat.room_12", destination(wire));
        assertEquals(SimpMessageHeaderAccessor.class, MessageHeaderAccessor.getAccessor(wire, MessageHeaderAccessor.class).getClass());
        assertEquals(destination(wire), StompHeaderAccessor.wrap(wire).getFirstNativeHeader("destination"));
        assertSame(original.getPayload(), wire.getPayload());
        assertEquals("alice", wire.getHeaders().get("chatRecipient"));
        assertEquals("/topic/chat/room_12", destination(original));
    }
    @Test void subscriptionsRetainCommandSessionAndSubscriptionId() {
        var h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        h.setDestination("/topic/chat/room/read"); h.setSessionId("test-session"); h.setSubscriptionId("read-1");
        var wire = StompHeaderAccessor.wrap(encode.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
        assertEquals("/topic/chat.room.read", wire.getDestination());
        assertEquals(StompCommand.SUBSCRIBE, wire.getCommand());
        assertEquals("test-session", wire.getSessionId());
        assertEquals("read-1", wire.getSubscriptionId());
    }
    @Test void brokerDeliveryReturnsToLogicalDestination() {
        for (String suffix : new String[]{"", "/read"}) {
            Message<?> original = applicationMessage("/topic/chat/room" + suffix);
            assertEquals(destination(original), destination(decode.preSend(encode.preSend(original, null), null)));
        }
    }
    @Test void UnresolvedUserQueuesAndInternalBroadcastsAreUntouched() {
        for (String path : new String[]{"/user/queue/events", "/queue/unrelated", "/topic/messenger-user-registry", "/pub/chat.send"}) {
            Message<?> message = applicationMessage(path);
            assertSame(message, encode.preSend(message, null));
            assertSame(message, decode.preSend(message, null));
        }
    }
    @Test void wildcardAndEncodedInputsAreNotNormalizedIntoAllowedRooms() {
        for (String path : new String[]{"/topic/chat/*", "/topic/chat/room/other", "/topic/chat.room.*", "/topic/chat%2Froom"}) {
            Message<?> message = applicationMessage(path);
            assertSame(message, encode.preSend(message, null));
            assertSame(message, decode.preSend(message, null));
        }
    }
    @Test void decodedDeliveryStillUsesExistingOutboundMembershipCheck() {
        JwtUtil jwt = mock(JwtUtil.class); ChatAccessService access = mock(ChatAccessService.class);
        when(jwt.validate("token")).thenReturn(true); when(jwt.getSubject("token")).thenReturn("alice");
        var security = new StompSecurityInterceptor(jwt, access);
        var h = StompHeaderAccessor.create(StompCommand.CONNECT);
        h.setSessionId("test-session"); h.setNativeHeader("Authorization", "Bearer token"); h.setLeaveMutable(true);
        security.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null);
        Message<?> delivery = decode.preSend(applicationMessage("/topic/chat.room.read"), null);
        assertNotNull(security.outbound().preSend(delivery, null));
        verify(access).requireMember(eq("room"), any());
        doThrow(new AccessDeniedException("revoked")).when(access).requireMember(eq("room"), any());
        assertNull(security.outbound().preSend(delivery, null));
    }

    @Test void personalQueuesUseDirectExchangeWithoutQueueRedeclaration() {
        for (String kind : new String[]{"events", "errors"}) {
            Message<?> original = applicationMessage("/queue/" + kind + "-usertest-session");
            Message<?> encoded = encode.preSend(original, null);
            assertEquals("/exchange/amq.direct/" + kind + "-usertest-session", destination(encoded));
            assertEquals(SimpMessageHeaderAccessor.class, MessageHeaderAccessor.getAccessor(encoded, MessageHeaderAccessor.class).getClass());
            assertEquals(destination(original), destination(decode.preSend(encoded, null)));
        }
    }
    @Test void personalSubscriptionsRemainExclusiveAndDoNotAcceptSharedQueueOverrides() {
        var h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        h.setDestination("/queue/events-usersession_12"); h.setSubscriptionId("events-1");
        h.setNativeHeader("x-queue-name", "some-other-queue");
        h.setNativeHeader("persistent", "true");
        h.setNativeHeader("x-dead-letter-exchange", "other");
        h.setNativeHeader("x-dead-letter-routing-key", "other");
        var wire = StompHeaderAccessor.wrap(encode.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
        assertEquals("/exchange/amq.direct/events-usersession_12", wire.getDestination());
        assertEquals("events-1", wire.getSubscriptionId());
        assertEquals("true", wire.getFirstNativeHeader("exclusive"));
        assertEquals("true", wire.getFirstNativeHeader("auto-delete"));
        assertEquals("false", wire.getFirstNativeHeader("durable"));
        for (String name : new String[]{"x-queue-name", "persistent", "x-dead-letter-exchange", "x-dead-letter-routing-key"}) {
            assertNull(wire.getFirstNativeHeader(name));
        }
    }
    @Test void arbitraryBrokerDestinationsAreNotDecodedIntoPersonalQueues() {
        for (String path : new String[]{"/exchange/other/events-usersession", "/exchange/amq.direct/events-user*", "/queue/events-user../other"}) {
            Message<?> message = applicationMessage(path);
            assertSame(message, encode.preSend(message, null));
            assertSame(message, decode.preSend(message, null));
        }
    }
    @Test void personalDeliveryStillChecksNativeRecipientAndCurrentRoomMembership() {
        JwtUtil jwt = mock(JwtUtil.class); ChatAccessService access = mock(ChatAccessService.class);
        when(jwt.validate("token")).thenReturn(true); when(jwt.getSubject("token")).thenReturn("alice");
        var security = new StompSecurityInterceptor(jwt, access);
        var connection = StompHeaderAccessor.create(StompCommand.CONNECT);
        connection.setSessionId("test-session"); connection.setNativeHeader("Authorization", "Bearer token"); connection.setLeaveMutable(true);
        security.preSend(MessageBuilder.createMessage(new byte[0], connection.getMessageHeaders()), null);
        var h = StompHeaderAccessor.create(StompCommand.MESSAGE);
        h.setSessionId("test-session"); h.setDestination("/exchange/amq.direct/events-usertest-session");
        h.setNativeHeader("chat-recipient", "alice"); h.setNativeHeader("chat-room", "room");
        Message<?> delivery = decode.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null);
        assertNotNull(security.outbound().preSend(delivery, null));
        verify(access).requireMember(eq("room"), any());
        h = StompHeaderAccessor.wrap(delivery);
        h.setNativeHeader("chat-recipient", "bob");
        assertNull(security.outbound().preSend(decode.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null), null));
        doThrow(new AccessDeniedException("revoked")).when(access).requireMember(eq("room"), any());
        assertNull(security.outbound().preSend(delivery, null));
    }
}
