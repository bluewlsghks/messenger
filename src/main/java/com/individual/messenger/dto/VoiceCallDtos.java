package com.individual.messenger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.UUID;

public final class VoiceCallDtos {
    private VoiceCallDtos() {}
    public record Start(@NotNull UUID callId, @NotNull UUID clientId, @NotBlank @Size(max = 100) String roomId) {}
    public record Candidate(@NotNull @Size(max = 2048) String candidate,
                            @Size(max = 32) String sdpMid, @Min(0) @Max(65535) Integer sdpMLineIndex,
                            @Size(max = 256) String usernameFragment) {}
    public record Command(@NotNull UUID clientId, @NotNull VoiceCallDtos.Action action,
                          @Size(max = 16000) String sdp, @Valid Candidate candidate) {}

    public record View(UUID id, String roomId, String callerId, String calleeId,
                       UUID callerClientId, UUID calleeClientId, String status, long expiresAt) {}
    public enum Action { ACCEPT, DECLINE, END, OFFER, ANSWER, ICE, PING }
}
