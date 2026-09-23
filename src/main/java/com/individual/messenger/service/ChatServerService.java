package com.individual.messenger.service;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.ServerInvite;
import com.individual.messenger.repo.ChatServerRepository;
import com.individual.messenger.repo.ServerInviteRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class ChatServerService {
    public static final int MAX_CHANNELS = 50;
    public static final int MAX_MEMBERS = 500;
    private final ChatServerRepository servers;
    private final ServerInviteRepository invites;
    private final MongoTemplate mongo;
    private final SecureRandom random = new SecureRandom();

    public ChatServerService(ChatServerRepository servers, ServerInviteRepository invites, MongoTemplate mongo) {
        this.servers = servers;
        this.invites = invites;
        this.mongo = mongo;
    }

    public List<ChatServer> list(String loginId) {
        return servers.findByMembersContainingOrderByCreatedAtAsc(loginId);
    }

    public ChatServer create(String loginId, String name) {
        ChatServer server = new ChatServer();
        server.name = cleanName(name, 80);
        server.ownerId = loginId;
        server.members.add(loginId);
        server.channels.add(new ChatServer.TextChannel("일반"));
        // Server, owner membership and default channel are one atomic document insert.
        return servers.insert(server);
    }

    public ChatServer requireMember(String serverId, String loginId) {
        ChatServer server = servers.findById(serverId).orElseThrow(ChatServerService::forbidden);
        if (server.members == null || !server.members.contains(loginId)) throw forbidden();
        return server;
    }

    public ChatServer requireOwner(String serverId, String loginId) {
        ChatServer server = requireMember(serverId, loginId);
        if (!loginId.equals(server.ownerId)) throw forbidden();
        return server;
    }

    public ChatServer.TextChannel createChannel(String serverId, String loginId, String rawName) {
        requireOwner(serverId, loginId);
        String name = cleanName(rawName, 40).toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        if (!name.matches("[\\p{L}\\p{N}_-]{1,40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "채널 이름에는 문자, 숫자, -, _만 사용할 수 있습니다.");
        }
        ChatServer.TextChannel channel = new ChatServer.TextChannel(name);
        Query query = Query.query(Criteria.where("id").is(serverId).and("ownerId").is(loginId)
                .and("channels.name").ne(name).and("channels." + (MAX_CHANNELS - 1)).exists(false));
        ChatServer updated = mongo.findAndModify(query, new Update().push("channels", channel),
                FindAndModifyOptions.options().returnNew(true), ChatServer.class);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "채널 이름이 중복되거나 채널 수 제한에 도달했습니다.");
        }
        return channel;
    }

    public IssuedInvite issueInvite(String serverId, String loginId) {
        requireOwner(serverId, loginId);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ServerInvite invite = new ServerInvite();
        invite.id = digest(code);
        invite.serverId = serverId;
        invite.createdBy = loginId;
        invite.expiresAt = Instant.now().plus(Duration.ofDays(7));
        invites.insert(invite);
        return new IssuedInvite(code, invite.expiresAt);
    }

    public ChatServer join(String loginId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("[A-Za-z0-9_-]{43}")) throw invalidInvite();
        ServerInvite invite = invites.findById(digest(code)).orElseThrow(ChatServerService::invalidInvite);
        // TTL deletion is asynchronous. Authorization must independently check expiry.
        if (invite.expiresAt == null || !invite.expiresAt.isAfter(Instant.now())) throw invalidInvite();
        Criteria capacity = new Criteria().orOperator(Criteria.where("members").is(loginId),
                Criteria.where("members." + (MAX_MEMBERS - 1)).exists(false));
        Query query = Query.query(new Criteria().andOperator(Criteria.where("id").is(invite.serverId), capacity));
        ChatServer joined = mongo.findAndModify(query, new Update().addToSet("members", loginId),
                FindAndModifyOptions.options().returnNew(true), ChatServer.class);
        if (joined == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "서버가 없거나 참여 인원 제한에 도달했습니다.");
        }
        return joined;
    }

    static String cleanName(String input, int maxLength) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank() || value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름의 길이 또는 문자가 유효하지 않습니다.");
        }
        return value;
    }

    static String digest(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "서버에 접근할 권한이 없습니다.");
    }
    private static ResponseStatusException invalidInvite() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "초대 코드가 유효하지 않거나 만료되었습니다.");
    }
    public record IssuedInvite(String code, Instant expiresAt) {}
}
