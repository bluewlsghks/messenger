package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "friends")
public class Friend {

    @Id
    private String id;
    private String ownerId;
    private String friendId;
    private LocalDateTime createdAt;

    public Friend() {}

    public Friend(String ownerId, String friendId, LocalDateTime createdAt) {
        this.ownerId = ownerId;
        this.friendId = friendId;
        this.createdAt = createdAt;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getFriendId() { return friendId; }
    public void setFriendId(String friendId) { this.friendId = friendId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
