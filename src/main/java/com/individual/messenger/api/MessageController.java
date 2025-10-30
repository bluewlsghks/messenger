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

    /** ✅ 메시지 저장(POST)은 하나만: JWT 인증 정보 사용 */
    @PostMapping
    public ResponseEntity<Message> post(
            Authentication auth,
            @RequestBody Map<String, String> body
    ) {
        String roomId = body.get("roomId");
        String content = body.get("content");

        // 기본적으로 JWT의 subject를 senderId로 사용
        String senderId = (auth != null) ? auth.getName() : body.getOrDefault("senderId", "anonymous");
        // 표시명은 요청에 들어오면 사용, 없으면 senderId
        String senderName = body.getOrDefault("senderName", senderId);

        Message saved = messageService.save(roomId, senderId, senderName, content);
        return ResponseEntity.ok(saved);
    }

    /** ✅ 페이징 목록: GET /api/messages?roomId=...&page=0&size=20 */
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

    /** ✅ 히스토리: GET /api/messages/{roomId}?before=ISO&limit=50 */
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
