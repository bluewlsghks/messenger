package com.individual.messenger.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public class User {
    @Id
    public String mongoId;

    @Indexed(unique = true)
    public String loginId;
    public String userName;

    @JsonIgnore
    public String passwordHash;

    @JsonIgnore
    public String phoneEnc;

    @JsonIgnore
    public String legacyPassword;

    @JsonIgnore
    public String legacyPhoneNumber;

    @JsonIgnore
    public String legacyId;

    public Instant createdAt = Instant.now();
}
