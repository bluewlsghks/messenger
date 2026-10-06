package com.individual.messenger.voice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoiceEventsInitializationTest {
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        return mock(ObjectProvider.class);
    }

    @Test
    void constructionDoesNotCreateBrokerInfrastructure() {
        ObjectProvider<SimpMessagingTemplate> messaging = provider();
        ObjectProvider<SimpUserRegistry> users = provider();

        new VoiceEvents(messaging, users);

        verifyNoInteractions(messaging, users);
    }

    @Test
    void presenceLookupOnlyResolvesTheUserRegistry() {
        ObjectProvider<SimpMessagingTemplate> messaging = provider();
        ObjectProvider<SimpUserRegistry> users = provider();
        SimpUserRegistry registry = mock(SimpUserRegistry.class);
        when(users.getObject()).thenReturn(registry);
        when(registry.getUser("bob")).thenReturn(mock(SimpUser.class));
        VoiceEvents events = new VoiceEvents(messaging, users);

        assertTrue(events.online("bob"));
        assertFalse(events.online("offline-user"));
        verifyNoInteractions(messaging);
    }

    @Test
    void deliveryRetainsRecipientAndRoomAuthorizationHeaders() {
        ObjectProvider<SimpMessagingTemplate> messaging = provider();
        ObjectProvider<SimpUserRegistry> users = provider();
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        when(messaging.getObject()).thenReturn(template);
        VoiceCallService.View call = mock(VoiceCallService.View.class);
        when(call.roomId()).thenReturn("dm-123");
        VoiceEvents events = new VoiceEvents(messaging, users);

        events.send("bob", call, "RING", null, null, null, null);

        verify(template).convertAndSendToUser(eq("bob"), eq("/queue/events"),
                org.mockito.ArgumentMatchers.<Object>argThat(payload ->
                        payload instanceof VoiceEvents.Event event
                                && "VOICE_CALL".equals(event.type())
                                && "RING".equals(event.action())
                                && "dm-123".equals(event.roomId())
                                && event.call() == call),
                org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(headers ->
                        "bob".equals(headers.get("chatRecipient"))
                                && "dm-123".equals(headers.get("chatRoomId"))));
        verifyNoInteractions(users);
    }
}
