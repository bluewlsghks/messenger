package com.individual.messenger.repo;

import com.individual.messenger.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);

    // 레거시 호환: loginId 또는 기존 id 필드로도 조회
    @Query("{ $or: [ { 'loginId': ?0 }, { 'id': ?0 } ] }")
    Optional<User> findByAnyId(String anyId);
}
