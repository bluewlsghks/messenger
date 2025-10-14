package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {
    private final RoomRepository roomRepo;
    public RoomService(RoomRepository roomRepo) { this.roomRepo = roomRepo; }

    @Transactional
    public Room create(String type, List<String> members) {
        RoomType roomType = RoomType.valueOf(type.toUpperCase());
        Room room = new Room();
        room.type = roomType;
        room.members = members;
        room.membersKey = String.join("#", members.stream().sorted().toList());
        return roomRepo.save(room);
    }

    public Room get(String id) {
        return roomRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));
    }
}
