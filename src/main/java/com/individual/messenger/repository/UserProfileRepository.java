package com.individual.messenger.repository;

import com.individual.messenger.domain.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfileRepository {
    private final MongoTemplate mongo;
    public UserProfileRepository(MongoTemplate mongo) { this.mongo = mongo; }
    public void rename(String mongoId, String userName) {
        // Patch only the display name; never overwrite encrypted/legacy fields with nulls.
        mongo.updateFirst(Query.query(Criteria.where("mongoId").is(mongoId)),
                new Update().set("userName", userName), User.class);
    }
}
