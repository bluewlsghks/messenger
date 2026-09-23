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
    @Id public String id;
    public String roomId;
    public String senderId;
    public String senderName;
    public String content;
    public Instant createdAt = Instant.now();
    public Instant editedAt;
    public Instant deletedAt;
    public long version;
    public List<String> readBy = new ArrayList<>();
    public Message() {}
    public Message(String roomId, String sender, String content) {
        this.roomId = roomId; this.senderId = sender; this.senderName = sender; this.content = content;
    }
}
