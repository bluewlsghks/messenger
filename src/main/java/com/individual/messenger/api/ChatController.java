package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class ChatController {

    private final SimpMessageSendingOperations messaging;
    private final MessageService messageService;

    public ChatController(SimpMessageSendingOperations messaging, MessageService messageService) {
        this.messaging = messaging;
        this.messageService = messageService;
    }

    /**
     * ?´ë¼?´ì–¸?¸ì—??/pub/chat.send ë¡?ë°œí–‰
     * payload: { roomId, senderId, senderName, content }
     * - ?¤ì œ ?´ì˜?ì„  STOMP ?°ê²° ??JWTë¥?ê²€ì¦í•´ senderIdë¥??œë²„?ì„œ ? ë¢°???ˆê²Œ ê²°ì •?˜ëŠ” ê±?ê¶Œì¥
     */
    @MessageMapping("/chat.send")
    public void onSend(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String senderId = payload.getOrDefault("senderId", "unknown");
        String senderName = payload.getOrDefault("senderName", senderId);
        String content = payload.getOrDefault("content", "");

        // 1) DB ?€??
        Message saved = messageService.save(roomId, senderId, senderName, content);

        // 2) êµ¬ë…?ì—ê²??„ì†¡
        messaging.convertAndSend("/sub/chat/" + roomId, saved);
    }
}

