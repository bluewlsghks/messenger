package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.repo.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {
    private final MessageRepository messageRepo;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket push

    public MessageService(MessageRepository messageRepo, SimpMessagingTemplate messagingTemplate) {
        this.messageRepo = messageRepo;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Message send(String roomId, String sender, String content) {
        Message saved = messageRepo.save(new Message(roomId, sender, content));
        // WebSocket(STOMP) broadcast to subscribers of /sub/rooms/{roomId}
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId, saved);
        return saved;
    }

    public Page<Message> list(String roomId, int page, int size) {
        return messageRepo.findByRoomId(roomId, PageRequest.of(page, size));
    }
}
