package com.individual.messenger.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    public String clientRequestId;
    public String replyToId;
    public String threadId;
    public List<String> attachmentIds = new ArrayList<>();
    public List<String> mentions = new ArrayList<>();
    @JsonIgnore public String requestFingerprint;
    // The latest message state and its pending publication are one atomic MongoDB document.
    @JsonIgnore public boolean publicationPending;
    @JsonIgnore public boolean creationPublished;
    @JsonIgnore public Instant publishAfter;
    @JsonIgnore public Instant publicationLeaseUntil;
    @JsonIgnore public String publicationLease;
    @JsonIgnore public int publicationAttempts;
    public List<String> readBy = new ArrayList<>();
    public Message() {}
    public Message(String roomId, String sender, String content) {
        this.roomId = roomId; this.senderId = sender; this.senderName = sender; this.content = content;
    }
}
