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
     * ?대씪?댁뼵?몄뿉??/pub/chat.send 濡?諛쒗뻾
     * payload: { roomId, senderId, senderName, content }
     * - ?ㅼ젣 ?댁쁺?먯꽑 STOMP ?곌껐 ??JWT瑜?寃利앺빐 senderId瑜??쒕쾭?먯꽌 ?좊ː???덇쾶 寃곗젙?섎뒗 嫄?沅뚯옣
     */
    @MessageMapping("/chat.send")
    public void onSend(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String senderId = payload.getOrDefault("senderId", "unknown");
        String senderName = payload.getOrDefault("senderName", senderId);
        String content = payload.getOrDefault("content", "");

        // 1) DB ???
        Message saved = messageService.save(roomId, senderId, senderName, content);

        // 2) 援щ룆?먯뿉寃??꾩넚
        messaging.convertAndSend("/sub/chat/" + roomId, saved);
    }
}


