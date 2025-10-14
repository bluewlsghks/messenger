package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Document("rooms")
@CompoundIndex(name = "members_type_idx", def = "{ 'type': 1, 'members': 1 }")
@CompoundIndex(name = "membersKey_unique", def = "{ 'membersKey': 1 }", unique = true)
public class Room {
    @Id
    public String id;
    @Indexed
    public RoomType type = RoomType.DIRECT; // DIRECT or GROUP
    public List<String> members;            // usernames
    public String membersKey;               // e.g. "alice#bob" (sorted)
    public Instant createdAt = Instant.now();

    public Room() {}

    public static Room directOf(String a, String b) {
        String[] arr = new String[]{a, b};
        Arrays.sort(arr);
        Room r = new Room();
        r.type = RoomType.DIRECT;
        r.members = java.util.List.of(arr[0], arr[1]);
        r.membersKey = arr[0] + "#" + arr[1];
        return r;
    }
}
