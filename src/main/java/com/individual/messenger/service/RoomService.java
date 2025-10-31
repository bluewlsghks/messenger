package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;          // ??반드??추�?
import com.individual.messenger.repo.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepo;
    public RoomService(RoomRepository roomRepo) { this.roomRepo = roomRepo; }

    public Room create(String type, List<String> members) {
        Room room = new Room();
        // ??RoomType enum ?�용
        room.type = RoomType.valueOf(type.toUpperCase());
        room.members = members;
        return roomRepo.save(room);
    }

    /** ??로그?�한 ?�용?�의 �?목록 조회 */
    public List<Room> findRoomsByMember(String loginId) {
        return roomRepo.findByMembersContaining(loginId);
    }
}

