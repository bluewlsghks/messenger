package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.repository.ChatServerRepository;
import com.individual.messenger.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

/** Online transport invoked by the durable message outbox; delivery errors propagate for retry. */
@Service
public class ConversationEvents {
    private static final Logger log = LoggerFactory.getLogger(ConversationEvents.class);
    private final RoomRepository rooms;
    private final ChatServerRepository servers;
    private final SimpMessagingTemplate messaging;
    public ConversationEvents(RoomRepository rooms, ChatServerRepository servers, SimpMessagingTemplate messaging) {
        this.rooms = rooms; this.servers = servers; this.messaging = messaging;
    }
    public void publish(String type, Message message) {
        Room room = rooms.findById(message.roomId).orElse(null);
        List<String> members; String serverId = null; String label;
        if (room != null) {
            members = room.members;
            label = room.type == RoomType.DIRECT ? "다이렉트 메시지" : "그룹 대화";
        } else {
            var server = servers.findByChannelsId(message.roomId).orElse(null);
            if (server == null) return;
            members = server.members; serverId = server.id;
            label = server.name + " · #" + server.channels.stream().filter(c -> message.roomId.equals(c.id))
                    .map(c -> c.name).findFirst().orElse("채널");
        }
        if (members == null) return;
        var event = new Notice(type, message.roomId, label, serverId, message);
        for (String recipient : new LinkedHashSet<>(members)) {
            if (recipient == null || recipient.isBlank()) continue;
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setHeader("chatRoomId", message.roomId); headers.setHeader("chatRecipient", recipient);
            headers.setNativeHeader("chat-room",message.roomId);headers.setNativeHeader("chat-recipient",recipient);
            headers.setLeaveMutable(true);
            messaging.convertAndSendToUser(recipient, "/queue/events", event, headers.getMessageHeaders());
        }
    }
    public record Notice(String type, String roomId, String label, String serverId, Message message) {}
}
