package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.repository.MessageActionRepository;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@Service
public class MessageActionService {
    private final ChatAccessService access;
    private final MessageActionRepository messages;
    private final ConversationEvents events;
    private final SimpMessagingTemplate messaging;
    public MessageActionService(ChatAccessService access, MessageActionRepository messages,
                                ConversationEvents events, SimpMessagingTemplate messaging) {
        this.access = access; this.messages = messages; this.events = events; this.messaging = messaging;
    }
    public Message edit(Principal actor, String roomId, String messageId, long version, String content) {
        return change(actor, roomId, messageId, version, MessageService.validateContent(content));
    }
    public Message delete(Principal actor, String roomId, String messageId, long version) {
        return change(actor, roomId, messageId, version, null);
    }
    private Message change(Principal actor, String roomId, String messageId, long version, String content) {
        if (version < 0) throw new IllegalArgumentException("메시지 버전이 유효하지 않습니다.");
        String me = access.requireMember(roomId, actor).loginId;
        Message existing = messages.find(roomId, messageId);
        if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다.");
        if (!me.equals(existing.senderId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "자신의 메시지만 변경할 수 있습니다.");
        if (existing.deletedAt != null && content == null) return existing;
        Message changed = messages.change(roomId, messageId, me, version, content, Instant.now());
        if (changed == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "메시지가 이미 변경되었습니다. 다시 불러와 주세요.");
        events.publish(content == null ? "MESSAGE_DELETED" : "MESSAGE_UPDATED", changed);
        messaging.convertAndSend("/sub/chat/" + roomId, changed);
        return changed;
    }
    public List<Message> search(Principal actor, String roomId, String q) {
        access.requireMember(roomId, actor);
        if (q == null || q.isBlank() || q.length() > 100) throw new IllegalArgumentException("검색어는 1~100자여야 합니다.");
        return messages.search(roomId, q.strip());
    }
}
