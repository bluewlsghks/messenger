package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.User;
import com.individual.messenger.security.ChatAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Service
public class ChatMessagingService {
    private static final Logger log = LoggerFactory.getLogger(ChatMessagingService.class);
    private final MessageService messages;
    private final ChatAccessService access;
    private final OpenAiService ai;
    private final Executor executor;
    private final Semaphore aiSlots = new Semaphore(4);

    public ChatMessagingService(MessageService messages, ChatAccessService access, OpenAiService ai,
                                @Qualifier("aiExecutor") Executor executor) {
        this.messages = messages;
        this.access = access;
        this.ai = ai;
        this.executor = executor;
    }
    public Message send(String roomId, String content, Principal principal) {
        User sender = access.requireMember(roomId, principal);
        String text = MessageService.validateContent(content);
        Message saved = messages.save(roomId, sender.loginId, sender.userName, text);
        if (isAiCommand(text) && text.length() > 3) ask(roomId, text.substring(3).strip(), principal, saved.id);
        return saved;
    }
    static boolean isAiCommand(String text) {
        return text != null && (text.equals("/ai") || text.startsWith("/ai ") || text.startsWith("/ai\n"));
    }
    private void ask(String roomId, String question, Principal principal, String questionId) {
        if (question.isBlank()) return;
        if (!ai.isEnabled()) {
            bot(roomId, "AI 기능이 꺼져 있습니다. 서버 운영자가 명시적으로 활성화해야 합니다.");
            return;
        }
        if (!aiSlots.tryAcquire()) {
            bot(roomId, "AI 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    access.requireMember(roomId, principal);
                    var recent = messages.findRecentMessages(roomId, 50).stream()
                            .filter(m -> !java.util.Objects.equals(m.id, questionId)).toList();
                    String answer = ai.reply(recent, question);
                    access.requireMember(roomId, principal);
                    if (answer == null || answer.isBlank()) throw new IllegalStateException("Empty AI reply");
                    bot(roomId, answer.length() > 4000 ? answer.substring(0, 3997) + "..." : answer);
                } catch (Exception error) {
                    log.warn("AI reply failed: {}", error.getClass().getSimpleName());
                    // Recheck access before publishing an error to a room that may have become unavailable.
                    try {
                        access.requireMember(roomId, principal);
                        bot(roomId, "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
                    } catch (RuntimeException ignored) { /* Permission revoked or persistence unavailable. */ }
                } finally {
                    aiSlots.release();
                }
            }, executor);
        } catch (RuntimeException rejected) {
            aiSlots.release();
            log.warn("AI executor rejected request");
        }
    }
    private void bot(String roomId, String content) { messages.save(roomId, "AI_BOT", "AI_BOT", content); }
}
