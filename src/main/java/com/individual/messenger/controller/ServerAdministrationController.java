package com.individual.messenger.controller;
import com.individual.messenger.service.ServerAdministration;
import com.individual.messenger.security.ChatAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}")
public class ServerAdministrationController {
    private final ServerAdministration administration; private final ChatAccessService access;
    public ServerAdministrationController(ServerAdministration administration, ChatAccessService access) { this.administration = administration; this.access = access; }
    public record Command(@NotNull @Pattern(regexp = "LEAVE|KICK|BAN|UNBAN|DELETE|TRANSFER|PROMOTE|DEMOTE|REVOKE_INVITES") String action,
                          @Size(max = 100) String targetId) {}
    public record Permissions(@Size(max = 500) List<@NotBlank @Size(max = 100) String> readers,
                              @Size(max = 500) List<@NotBlank @Size(max = 100) String> writers) {}
    @PostMapping("/administration") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void command(Principal p, @PathVariable String serverId, @Valid @RequestBody Command body) {
        administration.command(serverId, access.actor(p).loginId, body.action(), body.targetId());
    }
    @PutMapping("/channels/{channelId}/permissions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permissions(Principal p, @PathVariable String serverId, @PathVariable String channelId,
                            @Valid @RequestBody Permissions body) { administration.permissions(serverId, access.actor(p).loginId, channelId, body.readers(), body.writers()); }
}
