package com.individual.messenger.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public final class RoomRequests {
    private RoomRequests() {}
    public record CreateRequest(String type, @NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> members) {}
    public record GroupRequest(@NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> members) {}
    public record DmRequest(@NotBlank @Size(max = 100) String peerId) {}
}
