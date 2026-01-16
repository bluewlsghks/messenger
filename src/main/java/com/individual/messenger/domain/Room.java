package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Document("rooms")
@CompoundIndex(name = "members_type_idx", def = "{ 'type': 1, 'members': 1 }")
@CompoundIndex(name = "membersKey_unique", def = "{ 'membersKey': 1 }", unique = true)
public class Room {
    @Id
    public String id;

    @Indexed
    public RoomType type;            // ✅ DIRECT or GROUP

    public List<String> members;     // usernames
    public String membersKey;        // e.g. "alice#bob" (sorted)
    public Instant createdAt = Instant.now();

    public Room() {}

    /** 1:1 방 팩토리 */
    public static Room directOf(String a, String b) {
        if (a == null || b == null) throw new IllegalArgumentException("member is null");

        String x = a.trim().toLowerCase(Locale.ROOT);
        String y = b.trim().toLowerCase(Locale.ROOT);
        if (x.equals(y)) throw new IllegalArgumentException("cannot create DM with self");

        String[] arr = new String[]{x, y};
        Arrays.sort(arr); // 이미 소문자라 기본 정렬 OK

        Room r = new Room();
        r.type = RoomType.DIRECT;
        r.members = List.of(arr);
        r.membersKey = String.join("#", arr);
        return r;
    }

    /** 그룹 방 팩토리 */
    public static Room groupOf(List<String> members) {
        Room r = new Room();
        r.type = RoomType.GROUP;

        String[] arr = members.toArray(String[]::new);
        Arrays.sort(arr);
        r.members = List.of(arr);

        r.membersKey = null; // 또는 아예 세팅하지 않기
        return r;
    }
}
