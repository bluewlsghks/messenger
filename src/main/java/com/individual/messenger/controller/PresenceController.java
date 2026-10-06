package com.individual.messenger.controller;
import com.individual.messenger.service.PresenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;
@RestController
@RequestMapping("/api/presence")
public class PresenceController {
    private final PresenceService presence;
    public PresenceController(PresenceService presence) { this.presence = presence; }
    public record Heartbeat(@NotNull UUID clientId, @Size(max = 100) String roomId, boolean typing) {}
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(Principal p, @Valid @RequestBody Heartbeat body) { presence.heartbeat(p, body.clientId(), body.roomId(), body.typing()); }
    @DeleteMapping("/{clientId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void offline(Principal p, @PathVariable UUID clientId) { presence.offline(p, clientId); }
    @GetMapping("/contacts") public List<PresenceService.Status> contacts(Principal p) { return presence.contacts(p); }
    @GetMapping("/rooms/{roomId}") public List<PresenceService.Status> room(Principal p, @PathVariable String roomId) { return presence.room(p, roomId); }
}
