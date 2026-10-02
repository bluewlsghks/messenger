package com.individual.messenger.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Document("rooms")
@CompoundIndex(name = "members_type_idx", def = "{ 'type': 1, 'members': 1 }")
@CompoundIndex(name = "room_member_key_unique_strings", def = "{ 'membersKey': 1 }", unique = true,
        partialFilter = "{ 'membersKey': { '$type': 2 } }")
public class Room {
    @Id public String id;
    public RoomType type;
    public List<String> members;
    public String membersKey;
    public Instant createdAt = Instant.now();

    public static String memberId(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 100) {
            throw new IllegalArgumentException("유효한 사용자 ID가 필요합니다.");
        }
        return value.strip();
    }
    public static String directKey(String a, String b) {
        List<String> pair = List.of(memberId(a), memberId(b)).stream().sorted().toList();
        if (pair.get(0).equals(pair.get(1))) throw new IllegalArgumentException("자기 자신과는 DM을 만들 수 없습니다.");
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return "direct:" + encoder.encodeToString(pair.get(0).getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(pair.get(1).getBytes(StandardCharsets.UTF_8));
    }
    public static Room directOf(String a, String b) {
        Room room = new Room(); room.type = RoomType.DIRECT;
        room.members = List.of(memberId(a), memberId(b)).stream().sorted().toList();
        room.membersKey = directKey(a, b);
        return room;
    }
    public static Room groupOf(List<String> members) {
        if (members == null) throw new IllegalArgumentException("참여자가 필요합니다.");
        List<String> normalized = members.stream().map(Room::memberId).distinct().sorted().toList();
        if (normalized.size() < 3 || normalized.size() > 50) {
            throw new IllegalArgumentException("그룹방은 서로 다른 사용자 3~50명이 필요합니다.");
        }
        Room room = new Room(); room.type = RoomType.GROUP; room.members = normalized;
        // New groups no longer compete for the single null entry in an old full unique index.
        room.membersKey = "group:" + UUID.randomUUID();
        return room;
    }
}
