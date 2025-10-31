package com.individual.messenger.repo;

import com.individual.messenger.domain.Friend;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FriendRepository extends MongoRepository<Friend, String> {
    boolean existsByOwnerIdAndFriendId(String ownerId, String friendId);
    List<Friend> findByOwnerId(String ownerId);
}
