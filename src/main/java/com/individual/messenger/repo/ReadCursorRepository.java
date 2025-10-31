package com.individual.messenger.repo;

import com.individual.messenger.domain.ReadCursor;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ReadCursorRepository extends MongoRepository<ReadCursor, String> {
    Optional<ReadCursor> findByRoomIdAndUsername(String roomId, String username);
}
