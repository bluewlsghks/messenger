package com.individual.messenger.service;

import com.individual.messenger.domain.ReadCursor;
import com.individual.messenger.domain.Room;
import com.individual.messenger.repo.ReadCursorRepository;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class DmService {
    private final RoomService rooms;
    private final ReadCursorRepository cursors;
    private final ChatAccessService access;
    public DmService(RoomService rooms, ReadCursorRepository cursors, ChatAccessService access) {
        this.rooms = rooms; this.cursors = cursors; this.access = access;
    }
    public Room startDm(String me, String other) { return rooms.createOrGetDirect(me, other); }
    public ReadCursor markRead(String roomId, String loginId) {
        access.requireMember(roomId, () -> loginId);
        ReadCursor cursor = cursors.findByRoomIdAndUsername(roomId, loginId).orElse(new ReadCursor(roomId, loginId));
        Instant now = Instant.now();
        if (cursor.lastReadAt == null || now.isAfter(cursor.lastReadAt)) cursor.lastReadAt = now;
        return cursors.save(cursor);
    }
}
