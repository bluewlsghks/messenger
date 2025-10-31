package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;          // ??諛섎뱶??異붽?
import com.individual.messenger.repo.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepo;
    public RoomService(RoomRepository roomRepo) { this.roomRepo = roomRepo; }

    public Room create(String type, List<String> members) {
        Room room = new Room();
        // ??RoomType enum ?ъ슜
        room.type = RoomType.valueOf(type.toUpperCase());
        room.members = members;
        return roomRepo.save(room);
    }

    /** ??濡쒓렇?명븳 ?ъ슜?먯쓽 諛?紐⑸줉 議고쉶 */
    public List<Room> findRoomsByMember(String loginId) {
        return roomRepo.findByMembersContaining(loginId);
    }
}


