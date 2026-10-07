package com.individual.messenger.dto;

import jakarta.validation.constraints.*;

public final class ServerRequests {
    private ServerRequests() {}
    public record NameRequest(@NotBlank @Size(max = 80) String name) {}
    public record JoinRequest(@NotBlank @Size(max = 100) String code) {}
}
