package com.individual.messenger.service;

import com.individual.messenger.controller.ChatController;
import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.User;
import com.individual.messenger.security.ChatAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMessagingServiceTest {
    final MessageService messages = mock(MessageService.class);
    final ChatAccessService access = mock(ChatAccessService.class);
    final OpenAiService ai = mock(OpenAiService.class);
    final ChatMessagingService chat = new ChatMessagingService(messages, access, ai, Runnable::run);
    final Principal principal = () -> "alice";
    void permit() {
        User user = new User(); user.loginId = "alice"; user.userName = "실제 표시 이름";
        when(access.requireWritable("room", principal)).thenReturn(user);
        when(messages.store(anyString(), anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class), nullable(String.class), anyList(), anyList())).thenReturn(new MessageService.Stored(new Message(), true));
    }
    @Test void senderIdentityComesOnlyFromAuthenticatedUser() {
        permit();
        new ChatController(chat).onSend(Map.of("roomId", "room", "content", "hello", "senderId", "bob", "senderName", "관리자"), principal);
        verify(messages).store("room", "alice", "실제 표시 이름", "hello", null, null, null, java.util.List.of(), java.util.List.of());
    }
    @Test void authorizationFailurePreventsPersistenceAndAi() {
        when(access.requireWritable("room", principal)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class, () -> chat.send("room", "/ai secret", principal));
        verifyNoInteractions(messages, ai);
    }
    @Test void blankAndOversizedContentCannotBeSaved() {
        permit();
        assertThrows(IllegalArgumentException.class, () -> chat.send("room", " ", principal));
        assertThrows(IllegalArgumentException.class, () -> chat.send("room", "x".repeat(4001), principal));
        verify(messages, never()).store(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
    @Test void legacyAiEndpointPersistsQuestionAndDisabledAiMakesNoExternalCall() {
        permit();
        new ChatController(chat).onAiAsk(Map.of("roomId", "room", "content", "hello"), principal);
        verify(messages).store("room", "alice", "실제 표시 이름", "/ai hello", null, null, null, java.util.List.of(), java.util.List.of());
        verify(ai, never()).reply(anyList(), anyString());
    }
    @Test void aiPrefixMustBeACommandNotAnOrdinaryWord() {
        assertTrue(ChatMessagingService.isAiCommand("/ai hello"));
        assertTrue(ChatMessagingService.isAiCommand("/ai\nhello"));
        assertFalse(ChatMessagingService.isAiCommand("/airplane"));
    }
    @Test void aiServiceIsOptionalWithoutApiKey() {
        assertFalse(new OpenAiService("", false, false).isEnabled());
        assertThrows(IllegalStateException.class, () -> new OpenAiService("", true, true));
    }
}
