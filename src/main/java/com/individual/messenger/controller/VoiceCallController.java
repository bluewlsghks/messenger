package com.individual.messenger.controller;

import com.individual.messenger.config.VoiceConfiguration;
import com.individual.messenger.dto.VoiceCallDtos;
import com.individual.messenger.dto.VoiceCallDtos.*;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.VoiceCallService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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



    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(configuration.forUser(access.actor(principal).loginId));
    }
    @PostMapping("/calls")
    @ResponseStatus(HttpStatus.CREATED)
    public VoiceCallDtos.View start(@Valid @RequestBody Start body, Principal principal) {
        return calls.start(principal, body.callId(), body.clientId(), body.roomId());
    }
    @PostMapping("/calls/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void command(@PathVariable UUID id, @Valid @RequestBody Command body, Principal principal) {
        calls.command(principal, id, body);
    }
}
