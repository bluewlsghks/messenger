package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

/** Covers every existing insertion path, including REST, STOMP, legacy DM and AI. */
@Component
public class MessageNotificationListener extends AbstractMongoEventListener<Message> {
    private final ConversationEvents events;
    public MessageNotificationListener(ConversationEvents events) { this.events = events; }
    @Override
    public void onAfterSave(AfterSaveEvent<Message> event) {
        events.publish("MESSAGE_CREATED", event.getSource());
    }
}
