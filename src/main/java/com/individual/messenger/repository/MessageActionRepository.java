package com.individual.messenger.repository;

import com.individual.messenger.domain.Message;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class MessageActionRepository {
    private final MongoTemplate mongo;
    public MessageActionRepository(MongoTemplate mongo) { this.mongo = mongo; }

    public Message find(String roomId, String messageId) {
        return mongo.findOne(Query.query(Criteria.where("id").is(messageId).and("roomId").is(roomId)), Message.class);
    }

    /** Preserve legacy version=0 matching and return the atomically changed document. */
    public Message change(String roomId, String messageId, String owner, long version, String content, Instant now) {
        Criteria versionMatch = version == 0 ? new Criteria().orOperator(
                Criteria.where("version").is(0), Criteria.where("version").exists(false))
                : Criteria.where("version").is(version);
        Criteria match = new Criteria().andOperator(Criteria.where("id").is(messageId).and("roomId").is(roomId)
                .and("senderId").is(owner).and("deletedAt").is(null), versionMatch);
        Update update = new Update().set("content", content == null ? "" : content).inc("version", 1)
                .set("publicationPending", true).set("publishAfter", now).set("publicationAttempts", 0);
        update.set(content == null ? "deletedAt" : "editedAt", now);
        return mongo.findAndModify(Query.query(match), update,
                FindAndModifyOptions.options().returnNew(true), Message.class);
    }

    public List<Message> search(String roomId, String term) {
        Query query = Query.query(Criteria.where("roomId").is(roomId).and("deletedAt").is(null)
                .and("content").regex(Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)))
                .with(Sort.by(Sort.Direction.DESC, "createdAt", "id")).limit(50).maxTime(Duration.ofSeconds(2));
        return mongo.find(query, Message.class);
    }

    public java.util.List<Message> searchCandidates(String roomId,String text,java.util.List<String> ids) {
        return mongo.find(Query.query(Criteria.where("roomId").is(roomId).and("deletedAt").is(null)
                .and("id").in(ids).and("content").regex(java.util.regex.Pattern.quote(text),"i"))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt","id")).limit(50),Message.class);
    }
}
