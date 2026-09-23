package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.Room;
import com.individual.messenger.repo.ChatServerRepository;
import com.individual.messenger.repo.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.LinkedHashSet;
import java.util.List;

/** Online delivery only. Persistence remains authoritative; this is not a durable outbox. */
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
        try {
            Room room = rooms.findById(message.roomId).orElse(null);
            List<String> members;
            String serverId = null;
            String label;
            if (room != null) {
                members = room.members;
                label = room.type == com.individual.messenger.domain.RoomType.DIRECT ? "다이렉트 메시지" : "그룹 대화";
            } else {
                var server = servers.findByChannelsId(message.roomId).orElse(null);
                if (server == null) return;
                members = server.members;
                serverId = server.id;
                String channel = server.channels.stream().filter(c -> message.roomId.equals(c.id)).map(c -> c.name).findFirst().orElse("채널");
                label = server.name + " · #" + channel;
            }
            if (members == null) return;
            var event = new Notice(type, message.roomId, label, serverId, message);
            for (String recipient : new LinkedHashSet<>(members)) {
                if (recipient == null || recipient.isBlank()) continue;
                try {
                    var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                    // Internal, non-native headers survive user-destination resolution.
                    headers.setHeader("chatRoomId", message.roomId);
                    headers.setHeader("chatRecipient", recipient);
                    headers.setLeaveMutable(true);
                    messaging.convertAndSendToUser(recipient, "/queue/events", event, headers.getMessageHeaders());
                } catch (RuntimeException failed) {
                    log.warn("Online message delivery failed: {}", failed.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException failed) {
            // A delivery failure must not turn an already persisted message into a failed POST.
            log.warn("Conversation event failed: {}", failed.getClass().getSimpleName());
        }
    }
    public record Notice(String type, String roomId, String label, String serverId, Message message) {}
}
