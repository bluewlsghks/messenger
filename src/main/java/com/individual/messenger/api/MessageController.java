package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.ChatMessagingService;
import com.individual.messenger.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messages;
    private final ChatMessagingService chat;
    private final ChatAccessService access;
    public MessageController(MessageService messages, ChatMessagingService chat, ChatAccessService access) {
        this.messages = messages;
        this.chat = chat;
        this.access = access;
    }
    @PostMapping
    public Message post(Principal actor, @Valid @RequestBody SendRequest body) {
        return chat.send(body.roomId(), body.content(), actor);
    }
    @GetMapping
    public Map<String, Object> list(Principal actor, @RequestParam String roomId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        access.requireMember(roomId, actor);
        var result = messages.list(roomId, page, size);
        return Map.of("page", result.getNumber(), "size", result.getSize(),
                "total", result.getTotalElements(), "items", result.getContent());
    }
    @GetMapping("/{roomId}")
    public List<Message> history(Principal actor, @PathVariable String roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        access.requireMember(roomId, actor);
        return messages.history(roomId, before, beforeId, limit);
    }
    @PostMapping("/read")
    public ResponseEntity<Void> markRead(Principal actor, @Valid @RequestBody ReadRequest body) {
        String reader = access.requireMember(body.roomId(), actor).loginId;
        messages.markRead(body.roomId(), body.messageIds(), reader);
        return ResponseEntity.noContent().build();
    }
    public record SendRequest(@NotBlank @Size(max = 100) String roomId,
                              @NotBlank @Size(max = 4000) String content) {}
    public record ReadRequest(@NotBlank @Size(max = 100) String roomId,
                              @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> messageIds) {}
}
