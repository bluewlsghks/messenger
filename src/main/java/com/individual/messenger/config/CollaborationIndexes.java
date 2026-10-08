package com.individual.messenger.config;

import com.individual.messenger.domain.Message;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class CollaborationIndexes {
    @Bean ApplicationRunner initializeCollaborationIndexes(MongoTemplate mongo) {
        return args -> {
            mongo.indexOps(Message.class).createIndex(new Index().on("roomId", Sort.Direction.ASC)
                    .on("pinned", Sort.Direction.ASC).on("id", Sort.Direction.ASC).named("message_pins"));
            mongo.indexOps("message_bookmarks").createIndex(new Index().on("owner", Sort.Direction.ASC)
                    .on("roomId", Sort.Direction.ASC).on("messageId", Sort.Direction.ASC)
                    .unique().named("private_bookmark_owner_room_message"));
        };
    }
}
