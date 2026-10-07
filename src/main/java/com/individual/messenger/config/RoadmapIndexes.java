package com.individual.messenger.config;
import com.individual.messenger.domain.Message;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RoadmapIndexes {
    @Bean ApplicationRunner initializeRoadmapIndexes(MongoTemplate mongo) { return args -> {
        boolean uniqueLogin=mongo.indexOps("users").getIndexInfo().stream().anyMatch(index -> index.isUnique()
                && index.getIndexFields().size()==1 && index.getIndexFields().getFirst().getKey().equals("loginId"));
        if(!uniqueLogin) {
            try { mongo.indexOps("users").createIndex(new Index().on("loginId",Sort.Direction.ASC).unique()
                    .partial(PartialIndexFilter.of(Criteria.where("loginId").type(2))).named("user_login_unique_strings")); }
            catch(org.springframework.dao.DataAccessException problem) {
                throw new IllegalStateException("Cannot enforce loginId uniqueness. Audit duplicate IDs and index conflicts in a backup first; no documents or indexes were deleted.",problem);
            }
        }
        mongo.indexOps("auth_sessions").createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(java.time.Duration.ZERO).named("session_expiry"));
        mongo.indexOps("auth_sessions").createIndex(new Index().on("userId", Sort.Direction.ASC).named("session_owner"));
        mongo.indexOps("presence_leases").createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(java.time.Duration.ZERO).named("presence_expiry"));
        mongo.indexOps("presence_leases").createIndex(new Index().on("userId", Sort.Direction.ASC).named("presence_user"));
        mongo.indexOps("contact_relations").createIndex(new Index().on("members", Sort.Direction.ASC).named("contact_members"));
        mongo.indexOps("request_budgets").createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(java.time.Duration.ZERO).named("budget_expiry"));
        mongo.indexOps(Message.class).createIndex(new Index().on("senderId", Sort.Direction.ASC)
                .on("clientRequestId", Sort.Direction.ASC).unique().named("message_request_unique")
                .partial(PartialIndexFilter.of(Criteria.where("clientRequestId").type(2))));
        mongo.indexOps(Message.class).createIndex(new Index().on("publicationPending", Sort.Direction.ASC)
                .on("publishAfter", Sort.Direction.ASC).named("message_outbox_ready"));
        mongo.indexOps(Message.class).createIndex(new Index().on("roomId", Sort.Direction.ASC).on("id", Sort.Direction.ASC)
                .named("message_full_sync"));
        mongo.indexOps(Message.class).createIndex(new Index().on("roomId", Sort.Direction.ASC).on("threadId", Sort.Direction.ASC)
                .on("id", Sort.Direction.ASC).named("message_thread"));
        mongo.indexOps("push_subscriptions").createIndex(new Index().on("expiresAt",Sort.Direction.ASC).expire(java.time.Duration.ZERO));
        mongo.indexOps("push_subscriptions").createIndex(new Index().on("owner",Sort.Direction.ASC));
        mongo.indexOps("push_jobs").createIndex(new Index().on("expiresAt",Sort.Direction.ASC).expire(java.time.Duration.ZERO));
        mongo.indexOps("push_jobs").createIndex(new Index().on("pending",Sort.Direction.ASC).on("nextAt",Sort.Direction.ASC));

        mongo.indexOps("media_state").createIndex(new Index().on("expiresAt",Sort.Direction.ASC).expire(java.time.Duration.ZERO));
        mongo.indexOps("voice_history").createIndex(new Index().on("expiresAt",Sort.Direction.ASC).expire(java.time.Duration.ZERO));
        mongo.indexOps("voice_history").createIndex(new Index().on("participants",Sort.Direction.ASC).on("endedAt",Sort.Direction.DESC));

    }; }
}
