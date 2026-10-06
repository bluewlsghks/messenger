package com.individual.messenger.voice;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Reuses the existing recipient + room authorization in StompSecurityInterceptor. */
@Component
public class VoiceEvents {
    private final SimpMessagingTemplate messaging;
    private final SimpUserRegistry users;
    public VoiceEvents(SimpMessagingTemplate messaging, SimpUserRegistry users) {
        this.messaging = messaging; this.users = users;
    }
    public boolean online(String user) { return users.getUser(user) != null; }
    public record Event(String type, String action, String roomId, VoiceCallService.View call,
                        UUID targetClientId, String sdp, VoiceCallController.Candidate candidate, String reason) {}
    public void send(String recipient, VoiceCallService.View call, String action, UUID targetClientId,
                     String sdp, VoiceCallController.Candidate candidate, String reason) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setHeader("chatRecipient", recipient);
        headers.setHeader("chatRoomId", call.roomId());
        headers.setLeaveMutable(true);
        messaging.convertAndSendToUser(recipient, "/queue/events",
                new Event("VOICE_CALL", action, call.roomId(), call, targetClientId, sdp, candidate, reason),
                headers.getMessageHeaders());
    }
}
