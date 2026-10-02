package com.individual.messenger.repo;

import com.individual.messenger.domain.ChatServer;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ChatServerRepository extends MongoRepository<ChatServer, String> {
    List<ChatServer> findByMembersContainingOrderByCreatedAtAsc(String loginId);
    Optional<ChatServer> findByChannelsId(String channelId);
}
