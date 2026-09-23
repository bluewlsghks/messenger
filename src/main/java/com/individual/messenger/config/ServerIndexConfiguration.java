package com.individual.messenger.config;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.ServerInvite;
import com.individual.messenger.domain.Message;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import java.time.Duration;

/** Explicit, additive indexes; does not drop or rebuild existing rooms indexes. */
@Configuration
public class ServerIndexConfiguration implements ApplicationRunner {
    private final MongoTemplate mongo;
    public ServerIndexConfiguration(MongoTemplate mongo) { this.mongo = mongo; }
    @Override
    public void run(ApplicationArguments args) {
        mongo.indexOps(ChatServer.class).ensureIndex(new Index().on("members", Sort.Direction.ASC).named("server_members"));
        mongo.indexOps(ChatServer.class).ensureIndex(new Index().on("channels.id", Sort.Direction.ASC).named("server_channels"));
        mongo.indexOps(ServerInvite.class).ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC)
                .expire(Duration.ZERO).named("invite_expiry"));
        mongo.indexOps(Message.class).ensureIndex(new Index().on("roomId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC).named("room_message_cursor"));
    }
}
