package com.individual.messenger.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One atomic bounded log per room. Array position derives the sequence; no preallocated sequence gaps. */
@Repository
public class MessageJournalRepository {
    public static final int CAPACITY = 1000;
    private final MongoTemplate mongo;
    public MessageJournalRepository(MongoTemplate mongo) { this.mongo = mongo; }
    private MongoCollection<Document> collection() {
        return mongo.getCollection("message_change_journals").withReadPreference(ReadPreference.primary());
    }
    public record Snapshot(String epoch, long sequence, List<String> ids) {}
    public Snapshot snapshot(String room) {
        Document key = new Document("_id", room);
        Document value = collection().find(key).first();
        if (value == null) {
        Document initial = new Document("epoch", UUID.randomUUID().toString()).append("sequence", 0L)
                .append("ids", List.of());
        update(key, new Document("$setOnInsert", initial));
        value = collection().find(key).first();
        }
        if (value == null) throw new IllegalStateException("동기화 기준이 변경되었습니다. 다시 시도해 주세요.");
        return new Snapshot(value.getString("epoch"), ((Number) value.get("sequence")).longValue(),
                List.copyOf(value.getList("ids", String.class, new ArrayList<>())));
    }
    public void append(String room, String messageId) {
        Document update = new Document("$inc", new Document("sequence", 1L))
                .append("$push", new Document("ids", new Document("$each", List.of(messageId)).append("$slice", -CAPACITY)))
                .append("$setOnInsert", new Document("epoch", UUID.randomUUID().toString()));
        update(new Document("_id", room), update);
    }
    private void update(Document key, Document update) {
        try { collection().updateOne(key, update, new UpdateOptions().upsert(true)); }
        catch (MongoWriteException race) {
            if (race.getError().getCode() != 11000) throw race;
            collection().updateOne(key, update, new UpdateOptions().upsert(true));
        }
    }
}
