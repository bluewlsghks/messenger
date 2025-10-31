package com.individual.messenger.api;

import com.individual.messenger.domain.Room;
import com.individual.messenger.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    public RoomController(RoomService roomService) { this.roomService = roomService; }

    /** ??Î∞??ùÏÑ± */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> req) {
        String type = (String) req.getOrDefault("type", "DIRECT");
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) req.get("members");
        Room room = roomService.create(type, members);
        return ResponseEntity.ok(Map.of(
                "id", room.id,
                "type", room.type.name(),
                "members", room.members
        ));
    }

    /** ???¥Í? ?çÌïú Î∞?Î™©Î°ù */
    @GetMapping("/my")
    public ResponseEntity<List<Room>> myRooms(Authentication auth) {
        String loginId = auth.getName(); // JWT ?∏Ï¶ù???¨Ïö©??
        List<Room> rooms = roomService.findRoomsByMember(loginId);
        return ResponseEntity.ok(rooms);
    }
}
