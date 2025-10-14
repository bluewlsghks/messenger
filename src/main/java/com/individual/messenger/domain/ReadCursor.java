package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("read_cursors")
@CompoundIndex(name = "room_user_unique", def = "{ 'roomId': 1, 'username': 1 }", unique = true)
public class ReadCursor {
    @Id public String id;
    public String roomId;
    public String username;
    public Instant lastReadAt = Instant.EPOCH;

    public ReadCursor() {}
    public ReadCursor(String roomId, String username) {
        this.roomId = roomId; this.username = username;
    }
}
