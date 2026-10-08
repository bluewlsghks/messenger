package com.individual.messenger.repository;

import com.individual.messenger.domain.Message;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class MessageCollaborationRepository {
    public static final int MAX_REACTORS = 1000;
    private final MongoTemplate mongo;
    public MessageCollaborationRepository(MongoTemplate mongo) { this.mongo = mongo; }

    private static Criteria target(String room, String id) {
        return Criteria.where("id").is(id).and("roomId").is(room).and("deletedAt").is(null);
    }
    private static Update pending() {
        return new Update().inc("version", 1).set("publicationPending", true)
                .set("publishAfter", Instant.now()).set("publicationAttempts", 0);
    }
    public Message find(String room, String id) {
        return mongo.findOne(Query.query(target(room, id)), Message.class);
    }
    public Message reaction(String room, String id, String key, String actor, boolean add) {
        String path = "reactions." + key;
        Criteria match = target(room, id);
        Update change = pending();
        if (add) {
            match = match.and(path).ne(actor).and(path + "." + (MAX_REACTORS - 1)).exists(false);
            change.addToSet(path, actor);
        } else {
            match = match.and(path).is(actor);
            change.pull(path, actor);
        }
        return mongo.findAndModify(Query.query(match), change,
                FindAndModifyOptions.options().returnNew(true), Message.class);
    }
    public Message pin(String room, String id, String actor, boolean pin) {
        Update change = pending().set("pinned", pin);
        if (pin) change.set("pinnedBy", actor).set("pinnedAt", Instant.now());
        else change.unset("pinnedBy").unset("pinnedAt");
        Criteria match = target(room, id).and("pinned");
        match = pin ? match.ne(true) : match.is(true);
        return mongo.findAndModify(Query.query(match), change,
                FindAndModifyOptions.options().returnNew(true), Message.class);
    }
    public List<Message> pins(String room, String after, int size) {
        Criteria match = Criteria.where("roomId").is(room).and("deletedAt").is(null).and("pinned").is(true);
        if (after != null) match = match.and("id").gt(new ObjectId(after));
        return mongo.find(Query.query(match).with(Sort.by("id")).limit(size), Message.class);
    }
    // No message bodies or other users' saved-state information in this collection.
    public void bookmark(String owner, String room, String id, boolean save) {
        var collection = mongo.getCollection("message_bookmarks");
        var key = new Document("owner", owner).append("roomId", room).append("messageId", id);
        if (!save) { collection.deleteOne(key); return; }
        Document value = new Document("_id", owner.length() + ":" + owner + ":" + id)
                .append("owner", owner).append("roomId", room).append("messageId", id);
        try { collection.updateOne(key, new Document("$setOnInsert", value),
                new com.mongodb.client.model.UpdateOptions().upsert(true)); }
        catch (com.mongodb.MongoWriteException race) {
            if (race.getError().getCode() != 11000) throw race;
            // Concurrent identical insert is already in the desired state.
        }
    }
    public List<Document> bookmarks(String owner, String room, String after, int size) {
        Document query = new Document("owner", owner).append("roomId", room);
        if (after != null) query.append("messageId", new Document("$gt", after));
        return mongo.getCollection("message_bookmarks").find(query).sort(new Document("messageId", 1))
                .limit(size).into(new java.util.ArrayList<>());
    }
    public List<String> savedIds(String owner, String room, List<String> ids) {
        var query = new Document("owner", owner).append("roomId", room).append("messageId", new Document("$in", ids));
        return mongo.getCollection("message_bookmarks").find(query).projection(new Document("messageId", 1))
                .map(d -> d.getString("messageId")).into(new java.util.ArrayList<>());
    }
    public List<Message> current(String room, List<String> ids, boolean includeDeleted) {
        Criteria match = Criteria.where("roomId").is(room).and("id").in(ids);
        if (!includeDeleted) match = match.and("deletedAt").is(null);
        return mongo.find(Query.query(match).with(Sort.by("id")), Message.class);
    }
}
