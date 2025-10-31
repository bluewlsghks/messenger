package com.individual.messenger.api.auth.dto;

import java.time.Instant;

public class RegisterResponse {
    private String id;
    private String loginId;
    private String userName;
    private Instant createdAt;

    public RegisterResponse(String id, String loginId, String userName, Instant createdAt) {
        this.id = id; this.loginId = loginId; this.userName = userName; this.createdAt = createdAt;
    }
    // getter
}
