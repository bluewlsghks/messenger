package com.individual.messenger.api;

import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.security.ChatAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final ChatAccessService access;
    private final CryptoService crypto;
    private final MongoTemplate mongo;
    public UserController(ChatAccessService access, CryptoService crypto, MongoTemplate mongo) {
        this.access = access; this.crypto = crypto; this.mongo = mongo;
    }
    @GetMapping("/me")
    public Profile me(Principal principal) {
        User user = access.actor(principal);
        String phone = user.phoneEnc == null || user.phoneEnc.isBlank() ? null : crypto.decryptString(user.phoneEnc);
        return new Profile(user.loginId, user.userName == null ? user.loginId : user.userName, phone, user.createdAt);
    }
    @PatchMapping("/me")
    public Profile rename(Principal principal, @Valid @RequestBody Rename request) {
        User user = access.actor(principal);
        String name = request.userName().strip();
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("표시 이름을 확인해 주세요.");
        mongo.updateFirst(Query.query(Criteria.where("mongoId").is(user.mongoId)), new Update().set("userName", name), User.class);
        return new Profile(user.loginId, name, null, user.createdAt);
    }
    public record Profile(String id, String userName, String phoneNumber, Instant createdAt) {}
    public record Rename(@NotBlank @Size(max = 40) String userName) {}
}
