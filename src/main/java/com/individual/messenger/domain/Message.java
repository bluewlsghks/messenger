package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("messages")
@CompoundIndex(name = "room_time_idx", def = "{ 'roomId': 1, 'createdAt': -1 }")
public class Message {
    @Id
    public String id;

    public String roomId;
    public String senderId;      // ë¡œê·¸??ID (JWT??subject)
    public String senderName;    // ?œì‹œ???´ë¦„
    public String content;

    public Instant createdAt = Instant.now();

    // ?•ì¥: ?½ìŒ ì²˜ë¦¬??
    public List<String> readBy = new ArrayList<>();

    public Message(String roomId, String sender, String content) {
        this.roomId = roomId;
        this.senderId = sender;     // ?¹ì? senderName ???¤ì œ ?„ë“œ ?´ë¦„??ë§ê²Œ
        this.senderName = sender;
        this.content = content;
    }

    public Message() {

    }
}

