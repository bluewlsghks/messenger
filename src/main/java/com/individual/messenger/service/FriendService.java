package com.individual.messenger.service;

import com.individual.messenger.domain.Friend;
import com.individual.messenger.domain.User;
import com.individual.messenger.dto.FriendDto;
import com.individual.messenger.repository.UserRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class FriendService {
    @org.springframework.beans.factory.annotation.Autowired private ContactService contacts;
    private final MongoTemplate mongo;
    private final UserRepository users;
    public FriendService(MongoTemplate mongo, UserRepository users) { this.mongo = mongo; this.users = users; }

    public void addFriend(String me, String rawPeer) { contacts.request(me, validPeer(rawPeer)); }
    public List<FriendDto> list(String me) {
        // Project only relation IDs: old optional timestamp values cannot break the friend list.
        Query query = Query.query(Criteria.where("ownerId").is(me));
        query.fields().include("friendId").exclude("_id");
        Set<String> ids = new LinkedHashSet<>();
        for (Document row : mongo.find(query, Document.class, "friends")) {
            if (row.get("friendId") instanceof String id && !id.isBlank() && !me.equals(id)) ids.add(id);
        }
        ids = contacts.friends(me, ids);
        if (ids.isEmpty()) return List.of();
        Map<String, User> byId = new HashMap<>();
        mongo.find(Query.query(Criteria.where("loginId").in(ids)), User.class)
                .forEach(user -> byId.put(user.loginId, user));
        return ids.stream().map(id -> {
            User user = byId.get(id);
            String name = user == null ? id + " (사용자 없음)" :
                    user.userName == null || user.userName.isBlank() ? id : user.userName;
            return new FriendDto(id, name);
        }).sorted(Comparator.comparing(FriendDto::getUserName).thenComparing(FriendDto::getUserId)).toList();
    }
    public void remove(String me, String rawPeer) { contacts.remove(me, validPeer(rawPeer)); }
    private static String validPeer(String peer) {
        if (peer == null || peer.isBlank() || peer.length() > 100) throw new IllegalArgumentException("친구 아이디를 확인해 주세요.");
        return peer.trim();
    }
}
