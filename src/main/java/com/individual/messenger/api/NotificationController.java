package com.individual.messenger.api;

import com.individual.messenger.domain.Message;
import com.individual.messenger.repo.ChatServerRepository;
import com.individual.messenger.repo.RoomRepository;
import com.individual.messenger.security.ChatAccessService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final RoomRepository rooms;
    private final ChatServerRepository servers;
    private final ChatAccessService access;
    private final MongoTemplate mongo;
    public NotificationController(RoomRepository rooms, ChatServerRepository servers, ChatAccessService access, MongoTemplate mongo) {
        this.rooms = rooms; this.servers = servers; this.access = access; this.mongo = mongo;
    }
    @GetMapping("/unread")
    public Map<String, Long> unread(Principal principal) {
        String me = access.actor(principal).loginId;
        Set<String> ids = new LinkedHashSet<>();
        rooms.findByMembersContaining(me).forEach(room -> ids.add(room.id));
        servers.findByMembersContainingOrderByCreatedAtAsc(me).forEach(server -> server.channels.forEach(channel -> ids.add(channel.id)));
        if (ids.isEmpty()) return Map.of();
        var match = Criteria.where("roomId").in(ids).and("senderId").ne(me).and("readBy").ne(me).and("deletedAt").is(null);
        var pipeline = Aggregation.newAggregation(Aggregation.match(match), Aggregation.group("roomId").count().as("count"));
        Map<String, Long> result = new LinkedHashMap<>();
        mongo.aggregate(pipeline, Message.class, Document.class).forEach(row -> result.put(row.getString("_id"), ((Number) row.get("count")).longValue()));
        return result;
    }
}
