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
     * ìµœì‹  ?œìœ¼ë¡?ê°€?¸ì˜¨ ??UI ?¸ì˜ë¥??„í•´ ?¤ë¦„ì°¨ìˆœ(ê³¼ê±°?’ìµœ???¼ë¡œ ?¤ì§‘??ë°˜í™˜
     */
    public List<Message> history(String roomId, Instant before, int limit) {
        if (limit <= 0) limit = 50;
        var pageable = PageRequest.of(0, limit);

        List<Message> desc;
        if (before != null) {
            desc = messageRepo.findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(roomId, before, pageable);
        } else {
            desc = messageRepo.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
        }

        Collections.reverse(desc); // UI?ì„œ ?„â†’?„ë˜ë¡??ì—°?¤ëŸ½ê²?ë³´ì´?„ë¡
        return desc;
    }

    /** ???½ìŒ ì²˜ë¦¬: ?? readerId )ê°€ roomId??messageIds?¤ì„ ?½ìŒ?¼ë¡œ ?œì‹œ */
    @Transactional
    public void markRead(String roomId, List<String> messageIds, String readerId) {
        if (messageIds == null || messageIds.isEmpty()) return;

        Query q = new Query(new Criteria().andOperator(
                Criteria.where("id").in(messageIds),
                Criteria.where("roomId").is(roomId),
                // ??ë©”ì‹œì§€??êµ³ì´ readBy???£ì? ?ŠìŒ (?í•˜ë©??œê±°)
                Criteria.where("senderId").ne(readerId)
        ));
        Update u = new Update().addToSet("readBy", readerId);
        mongoTemplate.updateMulti(q, u, Message.class);

        // ?½ìŒ ?´ë²¤??ë¸Œë¡œ?œìº?¤íŠ¸ ??ì°¸ì—¬?ë“¤??UI ê°±ì‹ 
        messagingTemplate.convertAndSend("/sub/chat/" + roomId + "/read", Map.of(
                "messageIds", messageIds,
                "readerId", readerId
        ));
    }
}
