package com.individual.messenger.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("auth_sessions")
public class AuthSession {
    @Id public String id;
    public String userId;
    @JsonIgnore public String currentHash;
    @JsonIgnore public List<String> consumedHashes = new ArrayList<>();
    public Instant createdAt;
    public Instant lastUsedAt;
    public Instant expiresAt;
    public boolean revoked;
    public int rotations;
}
