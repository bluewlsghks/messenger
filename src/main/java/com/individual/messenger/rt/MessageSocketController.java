package com.individual.messenger.rt;

import com.individual.messenger.domain.Message;
import com.individual.messenger.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class MessageSocketController {
    private final MessageService messageService;
    public MessageSocketController(MessageService messageService) { this.messageService = messageService; }

    /** Client sends to /pub/messages.send */
    @MessageMapping("/messages.send")
    public void handleSend(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String sender = payload.get("sender");
        String content = payload.get("content");
        messageService.send(roomId, sender, content);
    }
}
