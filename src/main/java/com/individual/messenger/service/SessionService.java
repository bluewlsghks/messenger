package com.individual.messenger.service;

import com.individual.messenger.domain.AuthSession;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Opaque, rotating refresh credentials. Only SHA-256 hashes are stored. No sliding lifetime. */
@Service
public class SessionService {
    private final MongoTemplate mongo;
    private final SecureRandom random = new SecureRandom();
    public record Issued(String sessionId, String userId, String refreshToken, Instant expiresAt) {}
    public SessionService(MongoTemplate mongo) { this.mongo = mongo; }
    public Issued open(String user) {
        Instant now = Instant.now();
        // Limit retained active sessions, without creating unbounded per-user credential history.
        var old = mongo.find(Query.query(Criteria.where("userId").is(user).and("revoked").is(false)
                        .and("expiresAt").gt(now)).with(org.springframework.data.domain.Sort.by("createdAt")), AuthSession.class);
        for (int i = 0; i <= old.size() - 20; i++) revoke(old.get(i).id);
        AuthSession session = new AuthSession(); session.id = secret(18); session.userId = user;
        String token = session.id + "." + secret(32); session.currentHash = hash(token);
        session.createdAt = now; session.lastUsedAt = now; session.expiresAt = now.plus(Duration.ofDays(7));
        mongo.insert(session);
        return new Issued(session.id, user, token, session.expiresAt);
    }
    public Issued rotate(String token) {
        String id = tokenId(token); String oldHash = hash(token); Instant now = Instant.now();
        AuthSession session = mongo.findById(id, AuthSession.class);
        if (session == null || session.revoked || !session.expiresAt.isAfter(now) || session.rotations >= 1024) throw invalid();
        // Revoke only a proven replay, not a guessed secret accompanying a known session ID.
        if (!equal(oldHash, session.currentHash)) {
            if (session.consumedHashes.contains(oldHash)) revoke(id);
            throw invalid();
        }
        String next = id + "." + secret(32);
        AuthSession changed = mongo.findAndModify(Query.query(Criteria.where("id").is(id).and("currentHash").is(oldHash)
                        .and("revoked").is(false).and("expiresAt").gt(now).and("rotations").lt(1024)),
                new Update().set("currentHash", hash(next)).set("lastUsedAt", now).push("consumedHashes", oldHash).inc("rotations", 1),
                FindAndModifyOptions.options().returnNew(true), AuthSession.class);
        if (changed == null) {
            AuthSession current = mongo.findById(id, AuthSession.class);
            if (current != null && current.consumedHashes.contains(oldHash)) revoke(id);
            throw invalid();
        }
        return new Issued(id, changed.userId, next, changed.expiresAt);
    }
    public boolean active(String id, String user) {
        return mongo.exists(Query.query(Criteria.where("id").is(id).and("userId").is(user)
                .and("revoked").is(false).and("expiresAt").gt(Instant.now())), AuthSession.class);
    }
    public List<AuthSession> list(String user) {
        return mongo.find(Query.query(Criteria.where("userId").is(user).and("revoked").is(false)
                .and("expiresAt").gt(Instant.now())).with(org.springframework.data.domain.Sort.by("createdAt")), AuthSession.class);
    }
    public void revokeOwned(String id, String user) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("userId").is(user)), new Update().set("revoked", true), AuthSession.class);
    }
    public void logout(String token) {
        if (token == null) return;
        try {
            AuthSession session = mongo.findById(tokenId(token), AuthSession.class);
            if (session != null && (equal(hash(token), session.currentHash) || session.consumedHashes.contains(hash(token)))) revoke(session.id);
        } catch (ResponseStatusException ignored) { /* Already absent or malformed; always clear the cookie. */ }
    }
    private void revoke(String id) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().set("revoked", true), AuthSession.class);
    }
    private String secret(int bytes) { byte[] value = new byte[bytes]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    static String tokenId(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{24}\\.[A-Za-z0-9_-]{43}")) throw invalid();
        return token.substring(0, 24);
    }
    private static String hash(String value) { return ChatServerService.digest(value); }
    private static boolean equal(String a, String b) { return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII)); }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 만료되었거나 재사용되었습니다. 다시 로그인해 주세요."); }
}
