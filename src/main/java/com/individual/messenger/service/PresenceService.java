package com.individual.messenger.service;

import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.repository.ChatServerRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Service
public class PresenceService {
    private final MongoTemplate mongo; private final ChatAccessService access;
    private final RoomRepository rooms; private final ChatServerRepository servers;
    private final FriendService friends;
    public PresenceService(MongoTemplate mongo, ChatAccessService access, RoomRepository rooms, ChatServerRepository servers, FriendService friends) {
        this.mongo = mongo; this.access = access; this.rooms = rooms; this.servers = servers; this.friends = friends;
    }
    private String key(String user, UUID client) { return ChatServerService.digest(user + ":" + client); }
    public void heartbeat(Principal actor, UUID client, String room, boolean typing) {
        String user = access.actor(actor).loginId;
        if (room != null) { if (typing) access.requireWritable(room, actor); else access.requireMember(room, actor); }
        var update = new Update().set("userId", user).set("roomId", room)
                .set("typingUntil", Date.from(Instant.now().plusSeconds(typing ? 6 : -1)))
                .set("expiresAt", Date.from(Instant.now().plusSeconds(75)));
        mongo.upsert(Query.query(Criteria.where("_id").is(key(user, client))), update, "presence_leases");
    }
    public void offline(Principal actor, UUID client) {
        mongo.remove(Query.query(Criteria.where("_id").is(key(access.actor(actor).loginId, client))), "presence_leases");
    }
    public record Status(String userId, boolean online, boolean typing) {}
    public List<Status> contacts(Principal actor) {
        String user = access.actor(actor).loginId;
        return statuses(friends.list(user).stream().map(com.individual.messenger.dto.FriendDto::getUserId).toList(), null);
    }
    public List<Status> room(Principal actor, String room) {
        access.requireMember(room, actor);
        var direct = rooms.findById(room).orElse(null);
        Collection<String> candidates = direct == null ? servers.findByChannelsId(room).orElseThrow().members : direct.members;
        List<String> members = candidates.stream().filter(id -> {
            try { access.requireMember(room, () -> id); return true; }
            catch (org.springframework.web.server.ResponseStatusException denied) { return false; }
        }).toList();
        return statuses(members, room);
    }
    private List<Status> statuses(Collection<String> users, String room) {
        if (users.isEmpty()) return List.of();
        var leases = mongo.find(Query.query(Criteria.where("userId").in(users).and("expiresAt").gt(Date.from(Instant.now()))), Document.class, "presence_leases");
        Set<String> online = new HashSet<>(), typing = new HashSet<>();
        for (Document lease : leases) {
            online.add(lease.getString("userId"));
            if (room != null && room.equals(lease.getString("roomId")) && lease.getDate("typingUntil").toInstant().isAfter(Instant.now())) typing.add(lease.getString("userId"));
        }
        return users.stream().map(id -> new Status(id, online.contains(id), typing.contains(id))).toList();
    }
}
