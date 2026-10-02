package com.individual.messenger.api;

import com.individual.messenger.domain.Room;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService rooms;
    private final ChatAccessService access;
    public RoomController(RoomService rooms, ChatAccessService access) { this.rooms = rooms; this.access = access; }
    @GetMapping({"", "/my"})
    public List<Room> myRooms(Principal actor) { return rooms.findRoomsByMember(access.actor(actor).loginId); }
    @PostMapping
    public Map<String, Object> create(Principal actor, @Valid @RequestBody CreateRequest request) {
        String me = access.actor(actor).loginId;
        String type = request.type() == null ? "DIRECT" : request.type().toUpperCase(Locale.ROOT);
        if ("GROUP".equals(type)) return view(rooms.createGroup(me, request.members()));
        List<String> members = request.members().stream().map(Room::memberId).toList();
        if (!members.contains(me)) throw new IllegalArgumentException("생성자는 채팅방에 참여해야 합니다.");
        return view(rooms.create(type, members));
    }
    @PostMapping("/dm")
    public Map<String, Object> createDm(Principal actor, @Valid @RequestBody DmRequest request) {
        return view(rooms.createOrGetDirect(access.actor(actor).loginId, request.peerId()));
    }
    @PostMapping("/group")
    public Map<String, Object> createGroup(Principal actor, @Valid @RequestBody GroupRequest request) {
        return view(rooms.createGroup(access.actor(actor).loginId, request.members()));
    }
    private Map<String, Object> view(Room room) { return Map.of("id", room.id, "type", room.type.name(), "members", room.members); }
    public record CreateRequest(String type, @NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> members) {}
    public record GroupRequest(@NotNull @Size(max = 50) List<@NotBlank @Size(max = 100) String> members) {}
    public record DmRequest(@NotBlank @Size(max = 100) String peerId) {}
}
