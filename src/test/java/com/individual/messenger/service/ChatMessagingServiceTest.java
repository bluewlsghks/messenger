package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.User;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.api.ChatController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatMessagingServiceTest {
    final MessageService messages = mock(MessageService.class);
    final ChatAccessService access = mock(ChatAccessService.class);
    final OpenAiService ai = mock(OpenAiService.class);
    final ChatMessagingService chat = new ChatMessagingService(messages, access, ai, Runnable::run);
    final Principal principal = () -> "alice";
    void permit() {
        User user = new User(); user.loginId = "alice"; user.userName = "실제 표시 이름";
        when(access.requireMember("room", principal)).thenReturn(user);
        when(messages.save(anyString(), anyString(), anyString(), anyString())).thenReturn(new Message());
    }
    @Test void senderIdentityComesOnlyFromAuthenticatedUser() {
        permit();
        new ChatController(chat).onSend(Map.of("roomId", "room", "content", "hello", "senderId", "bob", "senderName", "관리자"), principal);
        verify(messages).save("room", "alice", "실제 표시 이름", "hello");
    }
    @Test void authorizationFailurePreventsPersistenceAndAi() {
        when(access.requireMember("room", principal)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> chat.send("room", "/ai secret", principal));
        verifyNoInteractions(messages, ai);
    }
    @Test void blankAndOversizedContentCannotBeSaved() {
        permit();
        assertThrows(IllegalArgumentException.class, () -> chat.send("room", " ", principal));
        assertThrows(IllegalArgumentException.class, () -> chat.send("room", "x".repeat(4001), principal));
        verify(messages, never()).save(anyString(), anyString(), anyString(), anyString());
    }
    @Test void legacyAiEndpointPersistsQuestionAndDisabledAiMakesNoExternalCall() {
        permit();
        new ChatController(chat).onAiAsk(Map.of("roomId", "room", "content", "hello"), principal);
        verify(messages).save("room", "alice", "실제 표시 이름", "/ai hello");
        verify(ai, never()).reply(anyList(), anyString());
    }
    @Test void aiPrefixMustBeACommandNotAnOrdinaryWord() {
        assertTrue(ChatMessagingService.isAiCommand("/ai hello"));
        assertTrue(ChatMessagingService.isAiCommand("/ai\nhello"));
        assertFalse(ChatMessagingService.isAiCommand("/airplane"));
    }
    @Test void aiServiceIsOptionalWithoutApiKey() {
        assertFalse(new OpenAiService("", false).isEnabled());
        assertThrows(IllegalStateException.class, () -> new OpenAiService("", true));
    }
}
