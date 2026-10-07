package com.individual.messenger.controller;

import com.individual.messenger.domain.Message;
import com.individual.messenger.dto.MessageRequests.Edit;
import com.individual.messenger.service.MessageActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/messages/{roomId}")
public class MessageActionController {
    private final MessageActionService messages;
    public MessageActionController(MessageActionService messages) { this.messages = messages; }
    @PatchMapping("/{messageId}")
    public Message edit(Principal actor, @PathVariable String roomId, @PathVariable String messageId,
                        @Valid @RequestBody Edit request) {
        return messages.edit(actor, roomId, messageId, request.version(), request.content());
    }
    @DeleteMapping("/{messageId}")
    public Message delete(Principal actor, @PathVariable String roomId, @PathVariable String messageId,
                          @RequestParam long version) {
        return messages.delete(actor, roomId, messageId, version);
    }
    @GetMapping("/search")
    public List<Message> search(Principal actor, @PathVariable String roomId, @RequestParam String q) {
        return messages.search(actor, roomId, q);
    }
}
