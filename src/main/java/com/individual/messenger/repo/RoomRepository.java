package com.individual.messenger.repo;

import com.individual.messenger.domain.Room;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * 대화방 컬렉션 접근용 Repository
 */
public interface RoomRepository extends MongoRepository<Room, String> {

    /** ✅ membersKey(DIRECT용)로 조회 */
    Optional<Room> findByMembersKey(String membersKey);

    /** ✅ 특정 사용자가 포함된 방 목록 조회 */
    List<Room> findByMembersContaining(String userId);
}
