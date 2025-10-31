package com.individual.messenger.service;

import com.individual.messenger.domain.ReadCursor;
import com.individual.messenger.domain.Room;
import com.individual.messenger.repo.ReadCursorRepository;
import com.individual.messenger.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DmService {
    private final RoomRepository roomRepo;
    private final ReadCursorRepository cursorRepo;

    public DmService(RoomRepository roomRepo, ReadCursorRepository cursorRepo) {
        this.roomRepo = roomRepo; this.cursorRepo = cursorRepo;
    }

    @Transactional
    public Room startDm(String a, String b) {
        if (a.equals(b)) throw new IllegalArgumentException("Self DM policy");
        String key = String.join("#", java.util.stream.Stream.of(a,b).sorted().toList());
        return roomRepo.findByMembersKey(key).orElseGet(() -> {
            Room room = Room.directOf(a, b);
            Room saved = roomRepo.save(room);
            cursorRepo.save(new ReadCursor(saved.id, a));
            cursorRepo.save(new ReadCursor(saved.id, b));
            return saved;
        });
    }

    @Transactional
    public ReadCursor markRead(String roomId, String username) {
        ReadCursor cur = cursorRepo.findByRoomIdAndUsername(roomId, username)
                .orElse(new ReadCursor(roomId, username));
        Instant now = Instant.now();
        if (now.isAfter(cur.lastReadAt)) cur.lastReadAt = now;
        return cursorRepo.save(cur);
    }
}
