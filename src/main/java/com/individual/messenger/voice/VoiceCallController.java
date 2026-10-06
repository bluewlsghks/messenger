package com.individual.messenger.voice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.individual.messenger.security.ChatAccessService;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/voice")
public class VoiceCallController {
    private final VoiceCallService calls;
    private final VoiceConfiguration configuration;
    private final ChatAccessService access;
    public VoiceCallController(VoiceCallService calls, VoiceConfiguration configuration, ChatAccessService access) {
        this.calls = calls; this.configuration = configuration; this.access = access;
    }
    public record Start(@NotNull UUID callId, @NotNull UUID clientId, @NotBlank @Size(max = 100) String roomId) {}
    public record Candidate(@NotNull @Size(max = 2048) String candidate,
                            @Size(max = 32) String sdpMid, @Min(0) @Max(65535) Integer sdpMLineIndex,
                            @Size(max = 256) String usernameFragment) {}
    public record Command(@NotNull UUID clientId, @NotNull VoiceCallService.Action action,
                          @Size(max = 16000) String sdp, @Valid Candidate candidate) {}
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(configuration.forUser(access.actor(principal).loginId));
    }
    @PostMapping("/calls")
    @ResponseStatus(HttpStatus.CREATED)
    public VoiceCallService.View start(@Valid @RequestBody Start body, Principal principal) {
        return calls.start(principal, body.callId(), body.clientId(), body.roomId());
    }
    @PostMapping("/calls/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void command(@PathVariable UUID id, @Valid @RequestBody Command body, Principal principal) {
        calls.command(principal, id, body);
    }
}
