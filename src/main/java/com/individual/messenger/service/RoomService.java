package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.repo.RoomRepository;
import com.individual.messenger.repo.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RoomService {
    private final RoomRepository rooms;
    private final UserRepository users;
    public RoomService(RoomRepository rooms, UserRepository users) { this.rooms = rooms; this.users = users; }
    public Room create(String type, List<String> members) {
        String normalizedType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if ("DIRECT".equals(normalizedType)) {
            if (members == null || members.size() != 2) throw new IllegalArgumentException("DM에는 두 명이 필요합니다.");
            return createOrGetDirect(members.get(0), members.get(1));
        }
        if ("GROUP".equals(normalizedType)) {
            Room group = Room.groupOf(members); group.members.forEach(this::requireUser); return rooms.save(group);
        }
        throw new IllegalArgumentException("지원하지 않는 채팅방 유형입니다.");
    }
    public Room createOrGetDirect(String me, String peer) {
        String a = Room.memberId(me); String b = Room.memberId(peer);
        String key = Room.directKey(a, b); requireUser(a); requireUser(b);
        var current = rooms.findByMembersKey(key);
        if (current.isPresent()) return verifyPair(current.get(), a, b);
        // Reuse legacy keys only when their stored participants exactly match these accounts.
        String legacyKey = String.join("#", List.of(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT)).stream().sorted().toList());
        var legacy = rooms.findByTypeAndMembersKey("DIRECT", legacyKey);
        if (legacy.isPresent() && exactPair(legacy.get(), a, b)) return legacy.get();
        try { return rooms.save(Room.directOf(a, b)); }
        catch (DuplicateKeyException race) {
            return verifyPair(rooms.findByMembersKey(key).orElseThrow(() -> race), a, b);
        }
    }
    public Room createGroup(String me, List<String> members) {
        List<String> all = new ArrayList<>(); if (members != null) all.addAll(members); all.add(Room.memberId(me));
        return create("GROUP", all);
    }
    public List<Room> findRoomsByMember(String loginId) { return rooms.findByMembersContaining(Room.memberId(loginId)); }
    private void requireUser(String id) {
        if ("AI_BOT".equalsIgnoreCase(id) || !users.existsByLoginId(id)) {
            throw new IllegalArgumentException("존재하지 않거나 사용할 수 없는 사용자 ID입니다.");
        }
    }
    private static boolean exactPair(Room room, String a, String b) {
        return room.type == RoomType.DIRECT && room.members != null && room.members.size() == 2
                && room.members.contains(a) && room.members.contains(b);
    }
    private static Room verifyPair(Room room, String a, String b) {
        if (!exactPair(room, a, b)) throw new IllegalArgumentException("기존 DM 참여자 정보의 점검이 필요합니다.");
        return room;
    }
}
