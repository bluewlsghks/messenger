package com.individual.messenger.repository;

import com.individual.messenger.domain.Message;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class UnreadMessageRepository {
    private final MongoTemplate mongo;
    public UnreadMessageRepository(MongoTemplate mongo) { this.mongo = mongo; }
    public Map<String, Long> count(Set<String> conversationIds, String reader) {
        if (conversationIds.isEmpty()) return Map.of();
        var match = Criteria.where("roomId").in(conversationIds).and("senderId").ne(reader)
                .and("readBy").ne(reader).and("deletedAt").is(null);
        var pipeline = Aggregation.newAggregation(Aggregation.match(match), Aggregation.group("roomId").count().as("count"));
        Map<String, Long> result = new LinkedHashMap<>();
        mongo.aggregate(pipeline, Message.class, Document.class)
                .forEach(row -> result.put(row.getString("_id"), ((Number) row.get("count")).longValue()));
        return result;
    }
}
