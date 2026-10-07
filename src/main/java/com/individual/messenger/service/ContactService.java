package com.individual.messenger.service;

import com.individual.messenger.domain.ContactRelation;
import com.individual.messenger.repository.UserRepository;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** One canonical relationship document makes approval and blocking atomic for both directions. */
@Service
public class ContactService {
    private final MongoTemplate mongo;
    private final UserRepository users;
    public ContactService(MongoTemplate mongo, UserRepository users) { this.mongo = mongo; this.users = users; }
    private String key(String me, String peer) {
        var ids = new ArrayList<>(List.of(me, peer)); Collections.sort(ids);
        return ChatServerService.digest(ids.get(0).length() + ":" + ids.get(0) + ":" + ids.get(1));
    }
    private boolean legacy(String me, String peer) {
        return mongo.exists(Query.query(Criteria.where("ownerId").is(me).and("friendId").is(peer)), "friends");
    }
    private void change(String me, String rawPeer, Consumer<ContactRelation> operation) {
        String peer = rawPeer == null ? "" : rawPeer.strip();
        if (peer.isEmpty() || peer.length() > 100 || me.equals(peer)) throw new IllegalArgumentException("상대 아이디를 확인해 주세요.");
        if ("AI_BOT".equalsIgnoreCase(peer) || users.findByLoginId(peer).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        String id = key(me, peer);
        for (int attempt = 0; attempt < 5; attempt++) {
            ContactRelation pair = mongo.findById(id, ContactRelation.class);
            if (pair == null) {
                pair = new ContactRelation(); pair.id = id; pair.members = List.of(me, peer);
                if (legacy(me, peer) || legacy(peer, me)) pair.state = "ACCEPTED";
            }
            operation.accept(pair); pair.updatedAt = Instant.now();
            try { mongo.save(pair); return; }
            catch (OptimisticLockingFailureException | DuplicateKeyException retry) { /* Re-evaluate against current relationship. */ }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "관계가 변경되었습니다. 다시 시도해 주세요.");
    }
    public void request(String me, String peer) {
        change(me, peer, pair -> {
            if (!pair.blockedBy.isEmpty()) throw forbidden();
            if (pair.state.equals("ACCEPTED")) return;
            if (pair.state.equals("REQUESTED")) {
                if (!me.equals(pair.requester)) throw new ResponseStatusException(HttpStatus.CONFLICT, "도착한 친구 요청을 먼저 수락해 주세요.");
                return;
            }
            pair.state = "REQUESTED"; pair.requester = me;
        });
    }
    public void respond(String me, String peer, String action) {
        change(me, peer, pair -> {
            if (!pair.blockedBy.isEmpty()) throw forbidden();
            if (!pair.state.equals("REQUESTED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "처리할 요청이 없습니다.");
            if (action.equals("CANCEL")) { if (!me.equals(pair.requester)) throw forbidden(); }
            else if (me.equals(pair.requester)) throw forbidden();
            pair.state = action.equals("ACCEPT") ? "ACCEPTED" : "NONE";
        });
    }
    public void remove(String me, String peer) { change(me, peer, pair -> pair.state = "NONE"); }
    public void block(String me, String peer, boolean blocked) {
        change(me, peer, pair -> { if (blocked) { pair.blockedBy.add(me); pair.state = "NONE"; } else pair.blockedBy.remove(me); });
    }
    public boolean blocked(String me, String peer) {
        ContactRelation pair = mongo.findById(key(me, peer), ContactRelation.class);
        return pair != null && !pair.blockedBy.isEmpty();
    }
    public Set<String> friends(String me, Collection<String> legacy) {
        Set<String> result = new LinkedHashSet<>(legacy);
        for (var pair : mongo.find(Query.query(Criteria.where("members").is(me)), ContactRelation.class)) {
            String peer = pair.members.stream().filter(id -> !id.equals(me)).findFirst().orElse("");
            if (pair.state.equals("ACCEPTED") && pair.blockedBy.isEmpty()) result.add(peer); else result.remove(peer);
        }
        return result;
    }
    public record Pending(String peerId, String requester, String direction, Instant updatedAt) {}
    public List<Pending> pending(String me) {
        return mongo.find(Query.query(Criteria.where("members").is(me).and("state").is("REQUESTED"))
                .with(org.springframework.data.domain.Sort.by("updatedAt")).limit(500), ContactRelation.class).stream()
                .filter(pair -> pair.blockedBy.isEmpty()).map(pair -> new Pending(
                        pair.members.stream().filter(id -> !id.equals(me)).findFirst().orElseThrow(), pair.requester,
                        me.equals(pair.requester) ? "OUTGOING" : "INCOMING", pair.updatedAt)).toList();
    }
    public List<String> blocks(String me) {
        return mongo.find(Query.query(Criteria.where("blockedBy").is(me)), ContactRelation.class).stream()
                .flatMap(pair -> pair.members.stream().filter(id -> !id.equals(me))).toList();
    }
    private ResponseStatusException forbidden() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "이 요청을 처리할 수 없습니다."); }
}
