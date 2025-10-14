package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Set;

@Document("messages")
@CompoundIndex(name = "room_created_idx", def = "{ 'roomId': 1, 'createdAt': -1 }")
public class Message {
    @Id
    public String id;
    @Indexed
    public String roomId;
    @Indexed
    public String sender;     // username
    public String content;
    public Instant createdAt = Instant.now();
    public Set<String> readBy = java.util.Set.of();

    public Message() {}
    public Message(String roomId, String sender, String content) {
        this.roomId = roomId; this.sender = sender; this.content = content;
    }
}
