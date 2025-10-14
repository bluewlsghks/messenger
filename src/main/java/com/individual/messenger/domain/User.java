package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("users")
public class User {
    @Id
    public String id;
    @Indexed(unique = true)
    public String username;
    public String displayName;
    public String status;
    public Instant createdAt = Instant.now();

    public User() {}
    public User(String username) { this.username = username; }
}
