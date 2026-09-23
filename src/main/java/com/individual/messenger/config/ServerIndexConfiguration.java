package com.individual.messenger.config;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.ServerInvite;
import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.Room;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import java.time.Duration;

/** Additive indexes only. Legacy duplicate string keys must be resolved before rollout. */
@Configuration
public class ServerIndexConfiguration implements ApplicationRunner {
    private final MongoTemplate mongo;
    public ServerIndexConfiguration(MongoTemplate mongo) { this.mongo = mongo; }
    @Override
    public void run(ApplicationArguments args) {
        mongo.indexOps(ChatServer.class).ensureIndex(new Index().on("members", Sort.Direction.ASC).named("server_members"));
        mongo.indexOps(ChatServer.class).ensureIndex(new Index().on("channels.id", Sort.Direction.ASC).named("server_channels"));
        mongo.indexOps(ServerInvite.class).ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("invite_expiry"));
        mongo.indexOps(Message.class).ensureIndex(new Index().on("roomId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC).named("room_message_cursor"));
        mongo.indexOps(Room.class).ensureIndex(new Index().on("membersKey", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("membersKey").type(2))).named("room_member_key_unique_strings"));
    }
}
