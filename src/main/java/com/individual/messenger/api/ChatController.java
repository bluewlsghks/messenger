package com.individual.messenger.api;

import com.individual.messenger.service.ChatMessagingService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.Map;

@Controller
public class ChatController {
    private final ChatMessagingService chat;
    public ChatController(ChatMessagingService chat) { this.chat = chat; }

    @MessageMapping("/chat.send")
    public void onSend(@Payload Map<String, String> payload, Principal principal) {
        chat.send(payload.get("roomId"), payload.get("content"), principal);
    }
    @MessageMapping("/ai.ask")
    public void onAiAsk(@Payload Map<String, String> payload, Principal principal) {
        String content = payload.get("content");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("질문을 입력해 주세요.");
        String text = content.strip();
        if (!(text.equals("/ai") || text.startsWith("/ai ") || text.startsWith("/ai\n"))) text = "/ai " + text;
        // Legacy endpoint now also persists the user's question, with server-derived identity.
        chat.send(payload.get("roomId"), text, principal);
    }
    @MessageExceptionHandler({IllegalArgumentException.class, ResponseStatusException.class})
    @SendToUser(value = "/queue/errors", broadcast = false)
    public Map<String, String> invalidRequest(Exception exception) {
        String message = exception instanceof ResponseStatusException status ? status.getReason() : exception.getMessage();
        return Map.of("error", "CHAT_REJECTED", "message", message == null ? "요청이 거부되었습니다." : message);
    }
}
