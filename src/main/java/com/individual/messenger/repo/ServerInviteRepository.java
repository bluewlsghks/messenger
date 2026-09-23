package com.individual.messenger.repo;

import com.individual.messenger.domain.ServerInvite;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ServerInviteRepository extends MongoRepository<ServerInvite, String> {}
