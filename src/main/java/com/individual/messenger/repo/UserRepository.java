package com.individual.messenger.repo;

import com.individual.messenger.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    boolean existsByLoginId(String loginId);

    // ?덇굅??臾몄꽌源뚯? ?ш큵?섎젮硫?
    Optional<User> findByLoginId(String loginId);
    Optional<User> findByLegacyId(String legacyId);

    // ?몄쓽??
    default Optional<User> findByAnyId(String anyId) {
        return findByLoginId(anyId).or(() -> findByLegacyId(anyId));
    }
}


