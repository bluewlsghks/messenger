package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.ChatMessagingService;
import com.individual.messenger.service.DmService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/dm")
public class DmController {
    private final DmService dm;
    private final ChatMessagingService chat;
    private final ChatAccessService access;
    public DmController(DmService dm, ChatMessagingService chat, ChatAccessService access) {
        this.dm = dm; this.chat = chat; this.access = access;
    }
    // Legacy clients may still submit me, but it is deliberately ignored on all three routes.
    @PostMapping("/start")
    public Map<String, Object> start(Principal actor, @RequestParam String other) {
        var room = dm.startDm(access.actor(actor).loginId, other);
        return Map.of("roomId", room.id, "members", room.members);
    }
    @PostMapping("/{roomId}/read")
    public Map<String, Object> read(Principal actor, @PathVariable String roomId) {
        String me = access.actor(actor).loginId;
        var cursor = dm.markRead(roomId, me);
        return Map.of("roomId", roomId, "me", me, "lastReadAt", cursor.lastReadAt);
    }
    @PostMapping("/{roomId}/send")
    public Message send(Principal actor, @PathVariable String roomId, @RequestParam String content) {
        return chat.send(roomId, content, actor);
    }
}
