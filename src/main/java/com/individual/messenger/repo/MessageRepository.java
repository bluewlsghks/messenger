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

    // 최신 히스토리 페이징 조회 (무한 스크롤: before 기준)
    List<Message> findByRoomIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            String roomId, Instant before, Pageable pageable
    );

    // 첫 페이지용(최신 limit개)
    List<Message> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    @Query(value = "{ 'roomId': ?0, 'createdAt': { $lt: ?1 } }", sort = "{ 'createdAt': -1 }", fields = "{}")
    List<Message> findTopByRoomIdAndCreatedAtBeforeOrderByCreatedAtDesc(String roomId, Instant before, int limit);

    List<Message> findTop50ByRoomIdOrderByCreatedAtDesc(String roomId);
}
