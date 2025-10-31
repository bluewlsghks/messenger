package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    public MessageController(MessageService messageService) { this.messageService = messageService; }

    /** ??硫붿떆吏 ???POST)? ?섎굹留? JWT ?몄쬆 ?뺣낫 ?ъ슜 */
    @PostMapping
    public ResponseEntity<Message> post(
            Authentication auth,
            @RequestBody Map<String, String> body
    ) {
        String roomId = body.get("roomId");
        String content = body.get("content");

        // 湲곕낯?곸쑝濡?JWT??subject瑜?senderId濡??ъ슜
        String senderId = (auth != null) ? auth.getName() : body.getOrDefault("senderId", "anonymous");
        // ?쒖떆紐낆? ?붿껌???ㅼ뼱?ㅻ㈃ ?ъ슜, ?놁쑝硫?senderId
        String senderName = body.getOrDefault("senderName", senderId);

        Message saved = messageService.save(roomId, senderId, senderName, content);
        return ResponseEntity.ok(saved);
    }

    /** ???섏씠吏?紐⑸줉: GET /api/messages?roomId=...&page=0&size=20 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Message> result = messageService.list(roomId, page, size);
        return ResponseEntity.ok(Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements(),
                "items", result.getContent()
        ));
    }

    /** ???덉뒪?좊━: GET /api/messages/{roomId}?before=ISO&limit=50 */
    @GetMapping("/{roomId}")
    public ResponseEntity<List<Message>> history(
            @PathVariable String roomId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(messageService.history(roomId, before, limit));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            Authentication auth,
            @RequestBody Map<String, Object> body
    ) {
        String roomId = (String) body.get("roomId");
        @SuppressWarnings("unchecked")
        List<String> messageIds = (List<String>) body.getOrDefault("messageIds", List.of());
        String readerId = auth.getName();

        messageService.markRead(roomId, messageIds, readerId);
        return ResponseEntity.noContent().build();
    }
}


