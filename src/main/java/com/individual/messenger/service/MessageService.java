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
     * 理쒖떊 ?쒖쑝濡?媛?몄삩 ??UI ?몄쓽瑜??꾪빐 ?ㅻ쫫李⑥닚(怨쇨굅?믪턀???쇰줈 ?ㅼ쭛??諛섑솚
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

        Collections.reverse(desc); // UI?먯꽌 ?꾟넂?꾨옒濡??먯뿰?ㅻ읇寃?蹂댁씠?꾨줉
        return desc;
    }

    /** ???쎌쓬 泥섎━: ?? readerId )媛 roomId??messageIds?ㅼ쓣 ?쎌쓬?쇰줈 ?쒖떆 */
    @Transactional
    public void markRead(String roomId, List<String> messageIds, String readerId) {
        if (messageIds == null || messageIds.isEmpty()) return;

        Query q = new Query(new Criteria().andOperator(
                Criteria.where("id").in(messageIds),
                Criteria.where("roomId").is(roomId),
                // ??硫붿떆吏??援녹씠 readBy???ｌ? ?딆쓬 (?먰븯硫??쒓굅)
                Criteria.where("senderId").ne(readerId)
        ));
        Update u = new Update().addToSet("readBy", readerId);
        mongoTemplate.updateMulti(q, u, Message.class);

        // ?쎌쓬 ?대깽??釉뚮줈?쒖틦?ㅽ듃 ??李몄뿬?먮뱾??UI 媛깆떊
        messagingTemplate.convertAndSend("/sub/chat/" + roomId + "/read", Map.of(
                "messageIds", messageIds,
                "readerId", readerId
        ));
    }
}


