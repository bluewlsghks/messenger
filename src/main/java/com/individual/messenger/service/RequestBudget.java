package com.individual.messenger.service;

import org.bson.Document;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Date;

@Service
public class RequestBudget {
    private final MongoTemplate mongo;
    public RequestBudget(MongoTemplate mongo) { this.mongo = mongo; }
    public void consume(String subject, String operation, int maximum, int seconds) {
        long slot = Instant.now().getEpochSecond() / seconds;
        String id = ChatServerService.digest(subject + ":" + operation + ":" + slot);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update().inc("count", 1).setOnInsert("expiresAt", Date.from(Instant.ofEpochSecond((slot + 2) * seconds)));
        Document value;
        try { value = mongo.findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, "request_budgets"); }
        catch (DuplicateKeyException race) { value = mongo.findAndModify(query, new Update().inc("count", 1),
                FindAndModifyOptions.options().returnNew(true), Document.class, "request_budgets"); }
        if (value == null || ((Number) value.get("count")).longValue() > maximum)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
    }
}
