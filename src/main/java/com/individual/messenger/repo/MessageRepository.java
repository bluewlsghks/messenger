package com.individual.messenger.repo;

import com.individual.messenger.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.Query;

public interface MessageRepository extends MongoRepository<Message, String> {
    Page<Message> findByRoomId(String roomId, Pageable pageable);

    // ìµœì‹  ?ˆìŠ¤? ë¦¬ ?˜ì´ì§?ì¡°íšŒ (ë¬´í•œ ?¤í¬ë¡? before ê¸°ì?)
    List<Message> findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            String roomId, Instant before, Pageable pageable
    );

    // ì²??˜ì´ì§€??ìµœì‹  limitê°?
    List<Message> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    @Query(value = "{ 'roomId': ?0, 'createdAt': { $lt: ?1 } }", sort = "{ 'createdAt': -1 }", fields = "{}")
    List<Message> findTopByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(String roomId, Instant before, int limit);
}

