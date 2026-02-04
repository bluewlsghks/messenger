package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.repo.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.core.query.Update;
import java.util.Map;
import org.springframework.data.domain.Sort;


@Service
public class MessageService {
    private final MessageRepository messageRepo;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket push
    private final MongoTemplate mongoTemplate;

    public MessageService(MessageRepository messageRepo, SimpMessagingTemplate messagingTemplate, MongoTemplate mongoTemplate) {
        this.messageRepo = messageRepo;
        this.messagingTemplate = messagingTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional
    public Message send(String roomId, String sender, String content) {
        Message saved = messageRepo.save(new Message(roomId, sender, content));
        // WebSocket(STOMP) broadcast to subscribers of /sub/rooms/{roomId}
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId, saved);
        return saved;
    }

    public Page<Message> list(String roomId, int page, int size) {
        return messageRepo.findByRoomId(roomId, PageRequest.of(page, size));
    }

    public Message save(String roomId, String senderId, String senderName, String content) {
        Message m = new Message();
        m.roomId = roomId;
        m.senderId = senderId;
        m.senderName = senderName;
        m.content = content;
        return messageRepo.save(m);
    }

    /**
     * 최신 순으로 가져온 뒤 UI 편의를 위해 오름차순(과거→최신)으로 뒤집어 반환
     */
    public List<Message> history(String roomId, Instant before, int limit) {
        if (before == null) before = Instant.now();

        Query q = new Query(Criteria.where("roomId").is(roomId)
                .and("createdAt").lt(before))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")) // 최신부터 뽑고
                .limit(limit);

        List<Message> desc = mongoTemplate.find(q, Message.class);
        java.util.Collections.reverse(desc); // ↩ 오름차순으로 뒤집어서 반환
        return desc;
    }

    /** ✅ 읽음 처리: 나( readerId )가 roomId의 messageIds들을 읽음으로 표시 */
    @Transactional
    public void markRead(String roomId, List<String> messageIds, String readerId) {
        if (messageIds == null || messageIds.isEmpty()) return;

        Query q = new Query(new Criteria().andOperator(
                Criteria.where("id").in(messageIds),
                Criteria.where("roomId").is(roomId),
                // 내 메시지는 굳이 readBy에 넣지 않음 (원하면 제거)
                Criteria.where("senderId").ne(readerId)
        ));
        Update u = new Update().addToSet("readBy", readerId);
        mongoTemplate.updateMulti(q, u, Message.class);

        // 읽음 이벤트 브로드캐스트 → 참여자들의 UI 갱신
        messagingTemplate.convertAndSend("/sub/chat/" + roomId + "/read", Map.of(
                "messageIds", messageIds,
                "readerId", readerId
        ));
    }

    public List<Message> findRecentMessages(String roomId, int limit) {
        List<Message> list =
                messageRepo.findTop50ByRoomIdOrderByCreatedAtDesc(roomId);
        Collections.reverse(list);
        return list;
    }

}
