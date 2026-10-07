package com.individual.messenger.controller;
import com.individual.messenger.service.ContactService;
import com.individual.messenger.security.ChatAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
    private final ContactService contacts; private final ChatAccessService access;
    public ContactController(ContactService contacts, ChatAccessService access) { this.contacts = contacts; this.access = access; }
    public record Response(@Pattern(regexp = "ACCEPT|DECLINE|CANCEL") @NotNull String action) {}
    @GetMapping("/requests") public List<ContactService.Pending> requests(Principal p) { return contacts.pending(access.actor(p).loginId); }
    @PostMapping("/requests/{peer}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void respond(Principal p, @PathVariable String peer, @Valid @RequestBody Response body) { contacts.respond(access.actor(p).loginId, peer, body.action()); }
    @GetMapping("/blocks") public List<String> blocks(Principal p) { return contacts.blocks(access.actor(p).loginId); }
    @PutMapping("/blocks/{peer}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(Principal p, @PathVariable String peer) { contacts.block(access.actor(p).loginId, peer, true); }
    @DeleteMapping("/blocks/{peer}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(Principal p, @PathVariable String peer) { contacts.block(access.actor(p).loginId, peer, false); }
}
