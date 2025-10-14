package com.individual.messenger.repo;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends MongoRepository<Room, String> {
    List<Room> findByTypeAndMembersIn(RoomType type, Iterable<String> members);
    Optional<Room> findByMembersKey(String membersKey);
}
