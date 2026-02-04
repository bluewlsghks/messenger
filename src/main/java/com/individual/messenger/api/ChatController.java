package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.service.MessageService;
import com.individual.messenger.service.OpenAiService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Controller
public class ChatController {

    private static final String TOPIC_PREFIX = "/sub/chat/";
    private static final String AI_BOT = "AI_BOT";

    private final SimpMessageSendingOperations messaging;
    private final MessageService messageService;
    private final OpenAiService openAiService;
    private final Executor aiExecutor;

    public ChatController(SimpMessageSendingOperations messaging,
                          MessageService messageService,
                          OpenAiService openAiService,
                          Executor aiExecutor) {
        this.messaging = messaging;
        this.messageService = messageService;
        this.openAiService = openAiService;
        this.aiExecutor = aiExecutor;
    }

    @MessageMapping("/chat.send")
    public void onSend(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String senderId = payload.getOrDefault("senderId", "unknown");
        String senderName = payload.getOrDefault("senderName", senderId);
        String content = payload.getOrDefault("content", "");

        // 1) 저장
        Message saved = messageService.save(roomId, senderId, senderName, content);

        // 2) 브로드캐스트
        broadcast(roomId, saved);

        // 3) "/ai"면 AI 처리 (무한루프 방지)
        if (isAiCommand(content) && !AI_BOT.equals(senderId)) {
            String question = extractAiQuestion(content);
            if (!question.isBlank()) {
                handleAiAsk(roomId, question);
            }
        }
    }

    @MessageMapping("/ai.ask")
    public void onAiAsk(@Payload Map<String, String> payload) {
        String roomId = payload.get("roomId");
        String content = payload.getOrDefault("content", "");

        String question = extractAiQuestion(content);
        if (question.isBlank()) return;

        handleAiAsk(roomId, question);
    }

    private void handleAiAsk(String roomId, String question) {
        CompletableFuture
                .supplyAsync(() -> {
                    List<Message> recent = messageService.findRecentMessages(roomId, 50);
                    return openAiService.reply(recent, question);
                }, aiExecutor)
                .thenAccept(aiText -> {
                    Message botSaved = messageService.save(roomId, AI_BOT, AI_BOT, aiText);
                    broadcast(roomId, botSaved);
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    Message botSaved = messageService.save(roomId, AI_BOT, AI_BOT,
                            "지금은 답변을 만들기 어렵네 😢 잠시 후 다시 시도해줘.");
                    broadcast(roomId, botSaved);
                    return null;
                });
    }

    private void broadcast(String roomId, Message message) {
        messaging.convertAndSend(TOPIC_PREFIX + roomId, message);
    }

    private boolean isAiCommand(String content) {
        return content != null && content.startsWith("/ai");
    }

    private String extractAiQuestion(String content) {
        if (content == null) return "";
        return content.replaceFirst("^/ai\\s*", "").trim();
    }
}
