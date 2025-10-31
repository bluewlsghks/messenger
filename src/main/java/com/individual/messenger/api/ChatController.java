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
     * 클라이언트에서 /pub/chat.send 로 발행
     * payload: { roomId, senderId, senderName, content }
     * - 실제 운영에선 STOMP 연결 시 JWT를 검증해 senderId를 서버에서 신뢰성 있게 결정하는 걸 권장
     */
    @MessageMapping("/chat.send")
    public void onSend(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String senderId = payload.getOrDefault("senderId", "unknown");
        String senderName = payload.getOrDefault("senderName", senderId);
        String content = payload.getOrDefault("content", "");

        // 1) DB 저장
        Message saved = messageService.save(roomId, senderId, senderName, content);

        // 2) 구독자에게 전송
        messaging.convertAndSend("/sub/chat/" + roomId, saved);
    }
}
