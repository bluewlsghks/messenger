package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.ConversationEvents;
import com.individual.messenger.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/messages/{roomId}")
public class MessageActionController {
    private final ChatAccessService access;
    private final MongoTemplate mongo;
    private final ConversationEvents events;
    private final SimpMessagingTemplate messaging;
    public MessageActionController(ChatAccessService access, MongoTemplate mongo, ConversationEvents events, SimpMessagingTemplate messaging) {
        this.access = access; this.mongo = mongo; this.events = events; this.messaging = messaging;
    }
    @PatchMapping("/{messageId}")
    public Message edit(Principal actor, @PathVariable String roomId, @PathVariable String messageId,
                        @Valid @RequestBody Edit request) {
        return change(actor, roomId, messageId, request.version(), MessageService.validateContent(request.content()));
    }
    @DeleteMapping("/{messageId}")
    public Message delete(Principal actor, @PathVariable String roomId, @PathVariable String messageId,
                          @RequestParam long version) {
        if (version < 0) throw new IllegalArgumentException("메시지 버전이 유효하지 않습니다.");
        return change(actor, roomId, messageId, version, null);
    }
    private Message change(Principal actor, String roomId, String messageId, long version, String content) {
        String me = access.requireMember(roomId, actor).loginId;
        Message existing = mongo.findOne(Query.query(Criteria.where("id").is(messageId).and("roomId").is(roomId)), Message.class);
        if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다.");
        if (!me.equals(existing.senderId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "자신의 메시지만 변경할 수 있습니다.");
        if (existing.deletedAt != null && content == null) return existing;
        Criteria versionMatch = version == 0 ? new Criteria().orOperator(Criteria.where("version").is(0), Criteria.where("version").exists(false)) : Criteria.where("version").is(version);
        Criteria match = new Criteria().andOperator(Criteria.where("id").is(messageId).and("roomId").is(roomId)
                .and("senderId").is(me).and("deletedAt").is(null), versionMatch);
        Update update = new Update().set("content", content == null ? "" : content).inc("version", 1);
        update.set(content == null ? "deletedAt" : "editedAt", Instant.now());
        Message changed = mongo.findAndModify(Query.query(match), update, FindAndModifyOptions.options().returnNew(true), Message.class);
        if (changed == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "메시지가 이미 변경되었습니다. 다시 불러와 주세요.");
        events.publish(content == null ? "MESSAGE_DELETED" : "MESSAGE_UPDATED", changed);
        // The shared workspace uses the user event feed; retain the original topic for older clients.
        messaging.convertAndSend("/sub/chat/" + roomId, changed);
        return changed;
    }
    @GetMapping("/search")
    public List<Message> search(Principal actor, @PathVariable String roomId, @RequestParam String q) {
        access.requireMember(roomId, actor);
        if (q == null || q.isBlank() || q.length() > 100) throw new IllegalArgumentException("검색어는 1~100자여야 합니다.");
        Query query = Query.query(Criteria.where("roomId").is(roomId).and("deletedAt").is(null)
                .and("content").regex(Pattern.compile(Pattern.quote(q.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)))
                .with(Sort.by(Sort.Direction.DESC, "createdAt", "id")).limit(50).maxTime(Duration.ofSeconds(2));
        return mongo.find(query, Message.class);
    }
    public record Edit(@NotBlank @Size(max = 4000) String content, @NotNull @PositiveOrZero Long version) {}
}
