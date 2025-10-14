package com.individual.messenger.api;

import com.individual.messenger.domain.ReadCursor;
import com.individual.messenger.domain.Room;
import com.individual.messenger.service.DmService;
import com.individual.messenger.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.individual.messenger.domain.Message;

import java.util.Map;

@RestController
@RequestMapping("/api/dm")
public class DmController {
    private final DmService dmService;
    private final MessageService messageService;
    public DmController(DmService dmService, MessageService messageService) {
        this.dmService = dmService; this.messageService = messageService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestParam String me, @RequestParam String other) {
        Room room = dmService.startDm(me, other);
        return ResponseEntity.ok(Map.of("roomId", room.id, "members", room.members));
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String roomId, @RequestParam String me) {
        ReadCursor cur = dmService.markRead(roomId, me);
        return ResponseEntity.ok(Map.of("roomId", roomId, "me", me, "lastReadAt", cur.lastReadAt));
    }

    @PostMapping("/{roomId}/send")
    public ResponseEntity<Message> send(@PathVariable String roomId, @RequestParam String me, @RequestParam String content) {
        return ResponseEntity.ok(messageService.send(roomId, me, content));
    }
}
