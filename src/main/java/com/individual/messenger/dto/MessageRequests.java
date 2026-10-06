package com.individual.messenger.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public final class MessageRequests {
    private MessageRequests() {}
    public record SendRequest(@NotBlank @Size(max = 100) String roomId,
                              @NotBlank @Size(max = 4000) String content,
                              @Pattern(regexp = "[0-9a-f-]{36}") String clientRequestId,
                              @Pattern(regexp = "[0-9a-f]{24}") String replyToId,
                              @Size(max = 5) List<@Pattern(regexp = "[0-9a-f]{24}") String> attachmentIds) {}
    public record ReadRequest(@NotBlank @Size(max = 100) String roomId,
                              @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> messageIds) {}

    public record Edit(@NotBlank @Size(max = 4000) String content, @NotNull @PositiveOrZero Long version) {}
}
