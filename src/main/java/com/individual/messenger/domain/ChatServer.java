package com.individual.messenger.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A small community. Membership is authoritative for every embedded text channel. */
@Document("chat_servers")
public class ChatServer {
    @Id public String id;
    public String name;
    public String ownerId;
    public List<String> members = new ArrayList<>();
    public List<TextChannel> channels = new ArrayList<>();
    public Instant createdAt = Instant.now();

    public static class TextChannel {
        public String id;
        public String name;
        public Instant createdAt;
        public TextChannel() {}
        public TextChannel(String name) {
            this.id = new ObjectId().toHexString();
            this.name = name;
            this.createdAt = Instant.now();
        }
    }
}
