package com.individual.messenger.repo;

import com.individual.messenger.domain.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    boolean existsByLoginId(String loginId);

    // ?àÍ±∞??Î¨∏ÏÑúÍπåÏ? ?¨Í¥Ñ?òÎ†§Î©?
    Optional<User> findByLoginId(String loginId);
    Optional<User> findByLegacyId(String legacyId);

    // ?∏Ïùò??
    default Optional<User> findByAnyId(String anyId) {
        return findByLoginId(anyId).or(() -> findByLegacyId(anyId));
    }
}

