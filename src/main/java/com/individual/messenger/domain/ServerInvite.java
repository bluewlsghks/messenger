package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/** The ID is a SHA-256 digest; the bearer invitation code is never persisted. */
@Document("server_invites")
public class ServerInvite {
    @Id public String id;
    public String serverId;
    public String createdBy;
    public Instant expiresAt;
}
