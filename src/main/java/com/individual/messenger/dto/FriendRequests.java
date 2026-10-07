package com.individual.messenger.dto;

import jakarta.validation.constraints.*;

public final class FriendRequests {
    private FriendRequests() {}
    public record FriendRequest(@NotBlank @Size(max = 100) String friendId) {}
}
