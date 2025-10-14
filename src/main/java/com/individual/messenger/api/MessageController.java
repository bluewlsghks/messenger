package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;
    public MessageController(MessageService messageService) { this.messageService = messageService; }

    @PostMapping
    public ResponseEntity<Message> send(@RequestBody Map<String, String> req) {
        Message msg = messageService.send(req.get("roomId"), req.get("sender"), req.get("content"));
        return ResponseEntity.ok(msg);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Message> result = messageService.list(roomId, page, size);
        return ResponseEntity.ok(Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements(),
                "items", result.getContent()
        ));
    }
}
