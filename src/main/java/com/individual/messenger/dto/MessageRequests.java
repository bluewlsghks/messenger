package com.individual.messenger.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public final class MessageRequests {
    private MessageRequests() {}
    public record SendRequest(@NotBlank @Size(max = 100) String roomId,
                              @NotBlank @Size(max = 4000) String content) {}
    public record ReadRequest(@NotBlank @Size(max = 100) String roomId,
                              @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> messageIds) {}

    public record Edit(@NotBlank @Size(max = 4000) String content, @NotNull @PositiveOrZero Long version) {}
}
