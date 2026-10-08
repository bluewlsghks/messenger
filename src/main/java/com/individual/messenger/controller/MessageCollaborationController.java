package com.individual.messenger.controller;

import com.individual.messenger.domain.Message;
import com.individual.messenger.dto.CollaborationDtos.*;
import com.individual.messenger.service.MessageCollaborationService;
import com.individual.messenger.service.MessageSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/messages/{roomId}")
public class MessageCollaborationController {
    private final MessageCollaborationService collaboration;
    private final MessageSyncService sync;
    public MessageCollaborationController(MessageCollaborationService collaboration, MessageSyncService sync) {
        this.collaboration = collaboration; this.sync = sync;
    }
    @GetMapping("/changes")
    public Changes changes(Principal actor, @PathVariable String roomId,
                           @RequestParam(required=false) String cursor, @RequestParam(defaultValue="100") int limit) {
        return sync.changes(actor, roomId, cursor, limit);
    }
    @GetMapping("/collaboration")
    public Capabilities capabilities(Principal actor, @PathVariable String roomId) {
        return collaboration.capabilities(actor, roomId);
    }
    @PutMapping("/{messageId}/reactions/{key}")
    public Message react(Principal actor, @PathVariable String roomId, @PathVariable String messageId, @PathVariable String key) {
        return collaboration.reaction(actor, roomId, messageId, key, true);
    }
    @DeleteMapping("/{messageId}/reactions/{key}")
    public Message unreact(Principal actor, @PathVariable String roomId, @PathVariable String messageId, @PathVariable String key) {
        return collaboration.reaction(actor, roomId, messageId, key, false);
    }
    @PutMapping("/{messageId}/pin")
    public Message pin(Principal actor, @PathVariable String roomId, @PathVariable String messageId) {
        return collaboration.pin(actor, roomId, messageId, true);
    }
    @DeleteMapping("/{messageId}/pin")
    public Message unpin(Principal actor, @PathVariable String roomId, @PathVariable String messageId) {
        return collaboration.pin(actor, roomId, messageId, false);
    }
    @PutMapping("/{messageId}/bookmark")
    public ResponseEntity<Void> save(Principal actor, @PathVariable String roomId, @PathVariable String messageId) {
        collaboration.bookmark(actor, roomId, messageId, true); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{messageId}/bookmark")
    public ResponseEntity<Void> unsave(Principal actor, @PathVariable String roomId, @PathVariable String messageId) {
        collaboration.bookmark(actor, roomId, messageId, false); return ResponseEntity.noContent().build();
    }
    @PostMapping("/snapshots")
    public List<Message> snapshots(Principal actor, @PathVariable String roomId, @RequestBody List<String> ids) {
        return collaboration.snapshots(actor, roomId, ids);
    }
    @PostMapping("/bookmark-status")
    public BookmarkState saved(Principal actor, @PathVariable String roomId, @RequestBody List<String> ids) {
        return collaboration.saved(actor, roomId, ids);
    }
    @GetMapping("/pins")
    public Page pins(Principal actor, @PathVariable String roomId,
                     @RequestParam(required=false) String after, @RequestParam(defaultValue="50") int limit) {
        return collaboration.pins(actor, roomId, after, limit);
    }
    @GetMapping("/bookmarks")
    public Page bookmarks(Principal actor, @PathVariable String roomId,
                          @RequestParam(required=false) String after, @RequestParam(defaultValue="50") int limit) {
        return collaboration.bookmarks(actor, roomId, after, limit);
    }
}
