package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.repo.RoomRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class RoomService {

    private final RoomRepository roomRepo;

    public RoomService(RoomRepository roomRepo) {
        this.roomRepo = roomRepo;
    }

    /** 공용 생성 (type에 따라 DIRECT/GROUP 자동 처리) */
    public Room create(String type, List<String> members) {
        if (type == null) throw new IllegalArgumentException("type is required");
        String t = type.toUpperCase(Locale.ROOT);

        if ("DIRECT".equals(t)) {
            if (members == null || members.size() != 2)
                throw new IllegalArgumentException("DIRECT는 정확히 2명이어야 합니다.");

            String a = members.get(0);
            String b = members.get(1);
            Room r = Room.directOf(a, b);

            try {
                return roomRepo.save(r);
            } catch (DuplicateKeyException race) {
                String key = makeMembersKey(a, b);
                return roomRepo.findByMembersKey(key).orElseThrow(() -> race);
            }
        }
        else if ("GROUP".equals(t)) {
            Room g = Room.groupOf(members);
            g.createdAt = Instant.now();
            return roomRepo.save(g);
        }
        else {
            throw new IllegalArgumentException("unknown type: " + type);
        }
    }

    /** 1:1 DM — 기존 있으면 재사용 */
    public Room createOrGetDirect(String me, String peer) {
        if (me == null || peer == null || me.isBlank() || peer.isBlank())
            throw new IllegalArgumentException("유효하지 않은 사용자 ID");
        if (me.equalsIgnoreCase(peer))
            throw new IllegalArgumentException("자기 자신과는 DM 불가");

        String key = makeMembersKey(me, peer);
        Optional<Room> found = roomRepo.findByMembersKey(key);
        if (found.isPresent()) return found.get();

        Room r = Room.directOf(me, peer);
        try {
            return roomRepo.save(r);
        } catch (DuplicateKeyException race) {
            return roomRepo.findByMembersKey(key).orElseThrow(() -> race);
        }
    }

    /** 그룹방 생성 (호출자 포함 3명 이상) */
    public Room createGroup(String me, List<String> members) {
        List<String> list = new ArrayList<>();
        if (members != null) list.addAll(members);
        if (me != null && !me.isBlank()) list.add(me);

        Room g = Room.groupOf(list);
        g.createdAt = Instant.now();
        return roomRepo.save(g);
    }

    /** 사용자 참여중인 방 */
    public List<Room> findRoomsByMember(String userId) {
        return roomRepo.findByMembersContaining(userId);
    }

    /** membersKey 생성 유틸 */
    private static String makeMembersKey(String a, String b) {
        String[] arr = { a, b };
        Arrays.sort(arr, String.CASE_INSENSITIVE_ORDER);
        return arr[0] + "#" + arr[1];
    }
}
