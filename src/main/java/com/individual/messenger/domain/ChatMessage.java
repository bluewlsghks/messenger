package com.individual.messenger.domain;

import java.time.Instant;

public class ChatMessage {
    public String roomId;
    public String sender;
    public String content;
    public Instant createdAt = Instant.now();
}


