package com.individual.messenger.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}
    public record Profile(String id, String userName, String phoneNumber, Instant createdAt) {}
    public record Rename(@NotBlank @Size(max = 40) String userName) {}
}
