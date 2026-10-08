package com.individual.messenger.service;

import com.individual.messenger.domain.Message;
import com.individual.messenger.dto.CollaborationDtos.*;
import com.individual.messenger.repository.ChatServerRepository;
import com.individual.messenger.repository.MessageCollaborationRepository;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.security.ChatAccessService;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Set;

@Service
public class MessageCollaborationService {
    public static final Set<String> REACTIONS = Set.of("like", "heart", "laugh", "party", "thanks", "eyes", "rocket");
    private final MessageCollaborationRepository repository;
    private final ChatAccessService access;
    private final RoomRepository rooms;
    private final ChatServerRepository servers;
    public MessageCollaborationService(MessageCollaborationRepository repository, ChatAccessService access,
                                       RoomRepository rooms, ChatServerRepository servers) {
        this.repository = repository; this.access = access; this.rooms = rooms; this.servers = servers;
    }
    public Message reaction(Principal actor, String room, String id, String key, boolean add) {
        String user = access.requireWritable(room, actor).loginId;
        validId(id);
        if (!REACTIONS.contains(key)) throw new IllegalArgumentException("지원하지 않는 메시지 반응입니다.");
        Message changed = repository.reaction(room, id, key, user, add);
        if (changed == null) {
            changed = requireMessage(room, id);
            boolean present = changed.reactions != null && changed.reactions.getOrDefault(key, List.of()).contains(user);
            if (present != add) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "반응 인원 한도 또는 동시 변경으로 처리하지 못했습니다. 다시 확인해 주세요.");
        }
        access.requireMember(room, actor);
        return changed;
    }
    public Message pin(Principal actor, String room, String id, boolean pin) {
        String user = access.requireWritable(room, actor).loginId;
        if (!pinManager(room, user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "서버 채널 고정은 소유자 또는 관리자만 변경할 수 있습니다.");
        validId(id);
        Message changed = repository.pin(room, id, user, pin);
        if (changed == null) changed = requireMessage(room, id);
        access.requireMember(room, actor);
        return changed;
    }
    private boolean pinManager(String room, String user) {
        if (rooms.existsById(room)) return true;
        return servers.findByChannelsId(room).map(s -> user.equals(s.ownerId)
                || s.moderators != null && s.moderators.contains(user)).orElse(false);
    }
    public Capabilities capabilities(Principal actor, String room) {
        String user = access.requireMember(room, actor).loginId;
        boolean writable;
        try { access.requireWritable(room, actor); writable = true; }
        catch (ResponseStatusException failure) {
            if (failure.getStatusCode().value() != 403) throw failure;
            writable = false;
        }
        return new Capabilities(writable, writable && pinManager(room, user));
    }
    public void bookmark(Principal actor, String room, String id, boolean save) {
        String user = access.requireMember(room, actor).loginId;
        validId(id);
        if (save) requireMessage(room, id);
        repository.bookmark(user, room, id, save);
        access.requireMember(room, actor);
    }
    public List<Message> snapshots(Principal actor, String room, List<String> ids) {
        access.requireMember(room, actor);
        if (ids == null || ids.size() > 100) throw new IllegalArgumentException("메시지는 최대 100개까지 확인합니다.");
        ids.forEach(MessageCollaborationService::validId);
        List<Message> result = repository.current(room, ids, true);
        access.requireMember(room, actor);
        return result;
    }
    public BookmarkState saved(Principal actor, String room, List<String> ids) {
        String user = access.requireMember(room, actor).loginId;
        if (ids == null || ids.size() > 1000) throw new IllegalArgumentException("메시지는 최대 1000개까지 확인합니다.");
        ids.forEach(MessageCollaborationService::validId);
        List<String> live = repository.current(room, ids, false).stream().map(m -> m.id).toList();
        var result = new BookmarkState(repository.savedIds(user, room, live));
        access.requireMember(room, actor);
        return result;
    }
    public Page pins(Principal actor, String room, String after, int limit) {
        access.requireMember(room, actor); page(after, limit);
        List<Message> rows = repository.pins(room, after, limit + 1);
        List<Message> items = rows.stream().limit(limit).toList();
        access.requireMember(room, actor);
        return new Page(items, items.isEmpty() ? after : items.getLast().id, rows.size() > limit);
    }
    public Page bookmarks(Principal actor, String room, String after, int limit) {
        String owner = access.requireMember(room, actor).loginId; page(after, limit);
        var rows = repository.bookmarks(owner, room, after, limit + 1);
        var scanned = rows.stream().limit(limit).toList();
        var ids = scanned.stream().map(d -> d.getString("messageId")).toList();
        List<Message> items = repository.current(room, ids, false);
        access.requireMember(room, actor);
        // Cursor advances across hidden/deleted records too, so an empty page is not EOF.
        return new Page(items, ids.isEmpty() ? after : ids.getLast(), rows.size() > limit);
    }
    private Message requireMessage(String room, String id) {
        Message message = repository.find(room, id);
        if (message == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지가 없거나 삭제되었습니다.");
        return message;
    }
    public static void validId(String id) {
        if (id == null || !id.matches("[0-9a-f]{24}") || !ObjectId.isValid(id))
            throw new IllegalArgumentException("메시지 ID가 유효하지 않습니다.");
    }
    private static void page(String after, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("조회 개수는 1~100이어야 합니다.");
        if (after != null) validId(after);
    }
}
