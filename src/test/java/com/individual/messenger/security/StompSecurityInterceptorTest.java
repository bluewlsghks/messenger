package com.individual.messenger.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StompSecurityInterceptorTest {
    final JwtUtil jwt = mock(JwtUtil.class);
    final ChatAccessService access = mock(ChatAccessService.class);
    final MessageChannel channel = mock(MessageChannel.class);
    final StompSecurityInterceptor interceptor = new StompSecurityInterceptor(jwt, access);
    @BeforeEach void setup() {
        when(jwt.validate("valid-token")).thenReturn(true);
        when(jwt.getSubject("valid-token")).thenReturn("alice");
    }
    Message<byte[]> frame(StompCommand command, String destination, boolean token) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("session-1");
        if (destination != null) headers.setDestination(destination);
        if (token) headers.setNativeHeader("Authorization", "Bearer valid-token");
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage("{}".getBytes(StandardCharsets.UTF_8), headers.getMessageHeaders());
    }
    void connect() { interceptor.preSend(frame(StompCommand.CONNECT, null, true), channel); }
    @Test void connectRequiresJwt() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.CONNECT, null, false), channel));
    }
    @Test void validConnectSetsAuthenticatedPrincipal() {
        Message<?> frame = frame(StompCommand.CONNECT, null, true);
        interceptor.preSend(frame, channel);
        assertEquals("alice", StompHeaderAccessor.wrap(frame).getUser().getName());
        assertFalse(StompHeaderAccessor.wrap(frame).getUser().toString().contains("valid-token"));
    }
    @Test void blocksSendBeforeConnectAndDirectBrokerPublish() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND, "/pub/chat.send", false), channel));
        connect();
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND, "/sub/chat/room", false), channel));
    }
    @Test void rejectsWildcardAndUnexpectedSubscriptions() {
        connect();
        for (String topic : new String[]{"/sub/chat/*", "/sub/chat/**", "/sub/chat/room/other", "/queue/errors", "/sub"}) {
            assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, topic, false), channel));
        }
    }
    @Test void membershipRequiredForMessagesAndReadReceipts() {
        connect();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireMember(eq("room"), any());
        assertThrows(ResponseStatusException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/sub/chat/room", false), channel));
        assertThrows(ResponseStatusException.class, () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/sub/chat/room/read", false), channel));
    }
    @Test void rejectsExpiredTokenAfterConnect() {
        connect(); when(jwt.validate("valid-token")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND, "/pub/chat.send", false), channel));
    }
    @Test void disconnectRemovesAuthentication() {
        connect(); interceptor.preSend(frame(StompCommand.DISCONNECT, null, false), channel);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(frame(StompCommand.SEND, "/pub/chat.send", false), channel));
    }
    @Test void outboundDeliveryRechecksMembershipAndExpiry() {
        connect();
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("session-1"); headers.setDestination("/sub/chat/room");
        Message<?> delivery = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertNotNull(interceptor.outbound().preSend(delivery, channel));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireMember(eq("room"), any());
        assertNull(interceptor.outbound().preSend(delivery, channel));
        when(jwt.validate("valid-token")).thenReturn(false);
        assertNull(interceptor.outbound().preSend(delivery, channel));
    }
}
