package com.individual.messenger.api;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.ChatServerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/servers")
public class ServerController {
    private final ChatServerService service;
    private final ChatAccessService access;
    public ServerController(ChatServerService service, ChatAccessService access) {
        this.service = service;
        this.access = access;
    }
    @GetMapping
    public List<ChatServer> list(Principal actor) {
        return service.list(access.actor(actor).loginId);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatServer create(Principal actor, @Valid @RequestBody NameRequest request) {
        return service.create(access.actor(actor).loginId, request.name());
    }
    @GetMapping("/{serverId}")
    public ChatServer get(Principal actor, @PathVariable String serverId) {
        return service.requireMember(serverId, access.actor(actor).loginId);
    }
    @GetMapping("/{serverId}/channels")
    public List<ChatServer.TextChannel> channels(Principal actor, @PathVariable String serverId) {
        return service.requireMember(serverId, access.actor(actor).loginId).channels;
    }
    @PostMapping("/{serverId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatServer.TextChannel createChannel(Principal actor, @PathVariable String serverId,
                                                @Valid @RequestBody NameRequest request) {
        return service.createChannel(serverId, access.actor(actor).loginId, request.name());
    }
    @PostMapping("/{serverId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatServerService.IssuedInvite invite(Principal actor, @PathVariable String serverId) {
        return service.issueInvite(serverId, access.actor(actor).loginId);
    }
    @PostMapping("/join")
    public ChatServer join(Principal actor, @Valid @RequestBody JoinRequest request) {
        return service.join(access.actor(actor).loginId, request.code());
    }
    public record NameRequest(@NotBlank @Size(max = 80) String name) {}
    public record JoinRequest(@NotBlank @Size(max = 100) String code) {}
}
