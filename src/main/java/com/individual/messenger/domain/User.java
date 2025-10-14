package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public class User {
    @Id
    public String mongoId;                    // Mongo _id

    @Indexed(unique = true)
    public String loginId;                    // 로그인용 ID (유니크)
    public String userName;

    public String passwordHash;               // BCrypt (단방향)
    public String phoneEnc;                   // AES-GCM (양방향)

    // 레거시 호환(있다면 자동 마이그레이션용, 선택)
    @org.springframework.data.mongodb.core.mapping.Field("id")
    public String legacyId;
    @org.springframework.data.mongodb.core.mapping.Field("password")
    public String legacyPassword;
    @org.springframework.data.mongodb.core.mapping.Field("phoneNumber")
    public String legacyPhoneNumber;

    public Instant createdAt = Instant.now();
}
