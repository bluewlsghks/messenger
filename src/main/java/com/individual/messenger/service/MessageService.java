package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.repository.MessageRepository;
import com.individual.messenger.security.ChatAccessService;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Persistence methods are internal; public transports authorize through ChatAccessService first. */
@Service
public class MessageService {
    public static final int MAX_CONTENT_LENGTH = 4000;
    public static final int MAX_PAGE_SIZE = 100;
    private final MessageRepository messageRepo;
    private final SimpMessagingTemplate messaging;
    private final MongoTemplate mongo;

    public MessageService(MessageRepository messageRepo, SimpMessagingTemplate messaging, MongoTemplate mongo) {
        this.messageRepo = messageRepo;
        this.messaging = messaging;
        this.mongo = mongo;
    }
    public Message send(String roomId, String sender, String content) {
        return save(roomId, sender, sender, content);
    }
    public Message save(String roomId, String senderId, String senderName, String content) {
        return store(roomId, senderId, senderName, content, null, null, null, List.of(), List.of()).message();
    }

    public record Stored(Message message, boolean created) {}

    public Stored store(String roomId, String senderId, String senderName, String content,
                        String requestId, String replyToId, String threadId,
                        List<String> attachments, List<String> mentions) {
        if (roomId == null || roomId.isBlank() || senderId == null || senderId.isBlank())
            throw new IllegalArgumentException("채팅방과 발신자가 필요합니다.");
        String text = validateContent(content);
        String key = requestId == null ? UUID.randomUUID().toString() : validRequestId(requestId);
        // Fingerprint is based on the original command, not the editable message content.
        String fingerprint = fingerprint(roomId, text, replyToId, attachments);
        Message existing = messageRepo.findBySenderIdAndClientRequestId(senderId, key).orElse(null);
        if (existing != null) return repeated(existing, fingerprint);
        Message message = new Message();
        message.roomId = roomId; message.senderId = senderId;
        message.senderName = senderName == null || senderName.isBlank() ? senderId : senderName;
        message.content = text; message.clientRequestId = key; message.requestFingerprint = fingerprint;
        message.replyToId = replyToId; message.threadId = threadId;
        message.attachmentIds = List.copyOf(attachments); message.mentions = List.copyOf(mentions);
        message.publicationPending = true; message.publishAfter = Instant.now();
        try { return new Stored(messageRepo.insert(message), true); }
        catch (DuplicateKeyException race) {
            return repeated(messageRepo.findBySenderIdAndClientRequestId(senderId, key).orElseThrow(() -> race), fingerprint);
        }
    }
    /** Check a retry before validating mutable parent/file state; current access is still required. */
    public Message replay(String roomId, String senderId, String content, String requestId,
                          String replyToId, List<String> attachments) {
        if (requestId == null) return null;
        String key = validRequestId(requestId);
        Message existing = messageRepo.findBySenderIdAndClientRequestId(senderId, key).orElse(null);
        if (existing == null) return null;
        List<String> files = attachments == null ? List.of() : attachments.stream().distinct().toList();
        return repeated(existing, fingerprint(roomId, validateContent(content), replyToId, files)).message();
    }
    private static String fingerprint(String roomId, String text, String replyToId, List<String> attachments) {
        return ChatServerService.digest(roomId.length() + ":" + roomId + text.length() + ":" + text
                + ":" + String.valueOf(replyToId) + ":" + String.join(",", attachments));
    }
    private static Stored repeated(Message message, String fingerprint) {
        if (!fingerprint.equals(message.requestFingerprint))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 전송 ID를 다른 메시지에 사용할 수 없습니다.");
        return new Stored(message, false);
    }
    private static String validRequestId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("clientRequestId는 소문자 UUID여야 합니다.");
        }
    }

    /** Full reconciliation has no 100-message total cap; each page is bounded to 100. */
    public List<Message> sync(String roomId, String afterId, int limit) {
        checkLimit(limit);
        Criteria match = Criteria.where("roomId").is(roomId);
        if (afterId != null) {
            if (!ObjectId.isValid(afterId)) throw new IllegalArgumentException("잘못된 동기화 커서입니다.");
            match = match.and("id").gt(new ObjectId(afterId));
        }
        return mongo.find(Query.query(match).with(Sort.by("id")).limit(limit), Message.class);
    }
    public List<Message> thread(String roomId, String rootId, String afterId, int limit) {
        checkLimit(limit);
        if (!ObjectId.isValid(rootId)) throw new IllegalArgumentException("잘못된 스레드입니다.");
        Criteria match = new Criteria().andOperator(Criteria.where("roomId").is(roomId),
                new Criteria().orOperator(Criteria.where("id").is(new ObjectId(rootId)), Criteria.where("threadId").is(rootId)));
        if (afterId != null) {
            if (!ObjectId.isValid(afterId)) throw new IllegalArgumentException("잘못된 커서입니다.");
            match = new Criteria().andOperator(match, Criteria.where("id").gt(new ObjectId(afterId)));
        }
        return mongo.find(Query.query(match).with(Sort.by("id")).limit(limit), Message.class);
    }
    public void markAllRead(String roomId, String readerId) {
        // One authoritative read model, including the old /api/dm read endpoint.
        mongo.updateMulti(Query.query(Criteria.where("roomId").is(roomId).and("createdAt").lte(Instant.now())
                .and("senderId").ne(readerId).and("readBy").ne(readerId)),
                new Update().addToSet("readBy", readerId), Message.class);
    }
    public static String validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("메시지는 1~4000자여야 합니다.");
        }
        return content.strip();
    }
    public Page<Message> list(String roomId, int page, int size) {
        checkLimit(size);
        if (page < 0 || page > 10000) throw new IllegalArgumentException("페이지 범위가 유효하지 않습니다.");
        return messageRepo.findByRoomId(roomId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }
    public List<Message> history(String roomId, Instant before, int limit) {
        return history(roomId, before, null, limit);
    }
    public List<Message> history(String roomId, Instant before, String beforeId, int limit) {
        Query query = historyQuery(roomId, before, beforeId, limit);
        List<Message> messages = new ArrayList<>(mongo.find(query, Message.class));
        Collections.reverse(messages);
        return messages;
    }
    static Query historyQuery(String roomId, Instant before, String beforeId, int limit) {
        checkLimit(limit);
        Criteria criteria = Criteria.where("roomId").is(roomId);
        if (beforeId != null) {
            if (before == null || !ObjectId.isValid(beforeId)) {
                throw new IllegalArgumentException("beforeId에는 before와 유효한 메시지 ID가 필요합니다.");
            }
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("createdAt").lt(before),
                    new Criteria().andOperator(Criteria.where("createdAt").is(before),
                            Criteria.where("id").lt(new ObjectId(beforeId)))));
        } else if (before != null) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("createdAt").lt(before));
        }
        return Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt", "id")).limit(limit);
    }
    public void markRead(String roomId, List<String> messageIds, String readerId) {
        if (messageIds == null || messageIds.isEmpty()) return;
        if (messageIds.size() > MAX_PAGE_SIZE || messageIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 100)) {
            throw new IllegalArgumentException("읽음 처리는 유효한 메시지 ID를 최대 100개까지 받습니다.");
        }
        Query query = Query.query(Criteria.where("id").in(messageIds).and("roomId").is(roomId)
                .and("senderId").ne(readerId).and("readBy").ne(readerId));
        query.fields().include("id");
        List<String> matched = mongo.find(query, Message.class).stream().map(m -> m.id).toList();
        if (matched.isEmpty()) return;
        Query updateQuery = Query.query(Criteria.where("id").in(matched).and("roomId").is(roomId));
        mongo.updateMulti(updateQuery, new Update().addToSet("readBy", readerId), Message.class);
        messaging.convertAndSend("/topic/chat/" + roomId + "/read", (Object) Map.of("messageIds", matched, "readerId", readerId));
    }
    public List<Message> findRecentMessages(String roomId, int limit) {
        return history(roomId, null, null, limit);
    }
    private static void checkLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) throw new IllegalArgumentException("조회 개수는 1~100이어야 합니다.");
    }
}
