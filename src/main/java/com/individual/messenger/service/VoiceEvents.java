package com.individual.messenger.service;

import com.individual.messenger.dto.VoiceCallDtos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/** Reuses recipient/room authorization without eagerly constructing the STOMP broker. */
@Component
public class VoiceEvents {
    private final Supplier<SimpMessagingTemplate> messaging;
    private final Supplier<SimpUserRegistry> users;
    @Autowired(required=false) private ObjectProvider<WebPushService> push;

    /** Resolve broker infrastructure only when an authenticated call actually uses it. */
    @Autowired
    public VoiceEvents(ObjectProvider<SimpMessagingTemplate> messaging,
                       ObjectProvider<SimpUserRegistry> users) {
        this.messaging = messaging::getObject;
        this.users = users::getObject;
    }

    /** Direct dependencies remain available to isolated service tests. */
    public VoiceEvents(SimpMessagingTemplate messaging, SimpUserRegistry users) {
        this.messaging = () -> messaging;
        this.users = () -> users;
    }

    public boolean online(String user) { return users.get().getUser(user) != null || (push!=null && push.getObject().reachable(user)); }

    public record Event(String type, String action, String roomId, VoiceCallDtos.View call,
                        UUID targetClientId, String sdp, VoiceCallDtos.Candidate candidate, String reason) {}

    public void send(String recipient, VoiceCallDtos.View call, String action, UUID targetClientId,
                     String sdp, VoiceCallDtos.Candidate candidate, String reason) {
        if("RING".equals(action) && push!=null)push.getObject().ring(recipient,call);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setHeader("chatRecipient", recipient);
        headers.setHeader("chatRoomId", call.roomId());
        headers.setNativeHeader("chat-room",call.roomId());headers.setNativeHeader("chat-recipient",recipient);
        headers.setLeaveMutable(true);
        messaging.get().convertAndSendToUser(recipient, "/queue/events",
                new Event("VOICE_CALL", action, call.roomId(), call, targetClientId, sdp, candidate, reason),
                headers.getMessageHeaders());
    }
    public void conference(String recipient,com.individual.messenger.dto.ConferenceDtos.Event event) {
        var headers=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setHeader("chatRecipient",recipient);headers.setHeader("chatRoomId",event.roomId());
        headers.setNativeHeader("chat-recipient",recipient);headers.setNativeHeader("chat-room",event.roomId());headers.setLeaveMutable(true);
        messaging.get().convertAndSendToUser(recipient,"/queue/events",event,headers.getMessageHeaders());
    }
}
