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

    /** ✅ 방 생성 */
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

    /** ✅ 내가 속한 방 목록 */
    @GetMapping("/my")
    public ResponseEntity<List<Room>> myRooms(Authentication auth) {
        String loginId = auth.getName(); // JWT 인증된 사용자
        List<Room> rooms = roomService.findRoomsByMember(loginId);
        return ResponseEntity.ok(rooms);
    }

    /** ✅ DM 생성/재사용 */
    @PostMapping("/dm")
    public ResponseEntity<Map<String, Object>> createDm(Authentication auth,
                                                        @RequestBody Map<String, String> req) {
        String me = auth.getName();
        String peer = req.get("peerId");
        Room room = roomService.createOrGetDirect(me, peer);
        return ResponseEntity.ok(Map.of(
                "id", room.id,
                "type", room.type.name(),
                "members", room.members
        ));
    }

    /** ✅ 그룹방 생성 */
    @PostMapping("/group")
    public ResponseEntity<Map<String, Object>> createGroup(Authentication auth,
                                                           @RequestBody Map<String, List<String>> req) {
        String me = auth.getName();
        List<String> members = req.get("members");
        Room room = roomService.createGroup(me, members);
        return ResponseEntity.ok(Map.of(
                "id", room.id,
                "type", room.type.name(),
                "members", room.members
        ));
    }
}
