package com.individual.messenger.security;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompSecurityInterceptor implements ChannelInterceptor {
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/sub/chat/([A-Za-z0-9_-]{1,100})(?:/read)?$");
    private static final Set<String> SEND_DESTINATIONS = Set.of("/pub/chat.send", "/pub/ai.ask");
    private static final Set<String> USER_TOPICS = Set.of("/user/queue/errors", "/user/queue/events");
    private final Map<String, SessionIdentity> sessions = new ConcurrentHashMap<>();
    private final JwtUtil jwt;
    private final ChatAccessService access;
    public StompSecurityInterceptor(JwtUtil jwt, ChatAccessService access) { this.jwt = jwt; this.access = access; }
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw denied();
        StompCommand command = headers.getCommand();
        String sessionId = headers.getSessionId();
        if (command == null && headers.getMessageType() == SimpMessageType.HEARTBEAT) return message;
        if (sessionId == null) throw denied();
        if (command == StompCommand.DISCONNECT) { sessions.remove(sessionId); return message; }
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String header = headers.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) throw denied();
            String token = header.substring(7);
            if (!jwt.validate(token)) throw denied();
            SessionIdentity identity;
            try { identity = new SessionIdentity(jwt.getSubject(token), token); access.actor(identity); }
            catch (RuntimeException error) { throw denied(); }
            if (sessions.putIfAbsent(sessionId, identity) != null) throw denied();
            headers.setUser(identity); return message;
        }
        SessionIdentity identity = sessions.get(sessionId);
        if (identity == null || !jwt.validate(identity.token)) throw denied();
        headers.setUser(identity);
        String destination = headers.getDestination();
        if (command == StompCommand.SUBSCRIBE) {
            if (destination == null || !USER_TOPICS.contains(destination)) access.requireMember(roomId(destination), identity);
            else access.actor(identity);
        } else if (command == StompCommand.SEND) {
            if (destination == null || !SEND_DESTINATIONS.contains(destination)) throw denied();
        } else if (command != StompCommand.UNSUBSCRIBE) throw denied();
        return message;
    }
    public ChannelInterceptor outbound() {
        return new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
                if (headers.getMessageType() != SimpMessageType.MESSAGE) return message;
                String sessionId = headers.getSessionId();
                SessionIdentity identity = sessionId == null ? null : sessions.get(sessionId);
                if (identity == null || !jwt.validate(identity.token)) return null;
                String destination = headers.getDestination();
                if (destination != null && destination.startsWith("/queue/errors-user")) return message;
                try {
                    if (destination != null && destination.startsWith("/queue/events-user")) {
                        if (!identity.getName().equals(headers.getHeader("chatRecipient"))) return null;
                        Object room = headers.getHeader("chatRoomId");
                        if (!(room instanceof String roomId)) return null;
                        access.requireMember(roomId, identity);
                    } else access.requireMember(roomId(destination), identity);
                    return message;
                } catch (RuntimeException denied) { return null; }
            }
        };
    }
    @EventListener public void disconnected(SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }
    static String roomId(String destination) {
        Matcher match = ROOM_TOPIC.matcher(destination == null ? "" : destination);
        if (!match.matches()) throw denied();
        return match.group(1);
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("STOMP access denied"); }
    private static final class SessionIdentity implements Principal {
        private final String name;
        private final String token;
        private SessionIdentity(String name, String token) { this.name = name; this.token = token; }
        @Override public String getName() { return name; }
        @Override public String toString() { return name; }
    }
}
