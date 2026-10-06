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
        assertEquals(StompCommand.SEND, StompHeaderAccessor.wrap(wire).getCommand());
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
    @Test void userQueuesAndInternalBroadcastsAreUntouched() {
        for (String path : new String[]{"/queue/events-userabc", "/topic/messenger-user-registry", "/pub/chat.send"}) {
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
}
