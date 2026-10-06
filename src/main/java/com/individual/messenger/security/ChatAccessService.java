package com.individual.messenger.security;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.User;
import com.individual.messenger.repository.ChatServerRepository;
import com.individual.messenger.repository.RoomRepository;
import com.individual.messenger.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/** Shared authorization for REST, STOMP SEND/SUBSCRIBE and outbound deliveries. */
@Service
public class ChatAccessService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.individual.messenger.service.ContactService contacts;
    private final RoomRepository rooms;
    private final ChatServerRepository servers;
    private final UserRepository users;

    public ChatAccessService(RoomRepository rooms, ChatServerRepository servers, UserRepository users) {
        this.rooms = rooms;
        this.servers = servers;
        this.users = users;
    }

    public User actor(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()
                || principal instanceof AnonymousAuthenticationToken
                || (principal instanceof Authentication auth && !auth.isAuthenticated())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        String loginId = principal.getName();
        if ("AI_BOT".equalsIgnoreCase(loginId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "예약된 시스템 계정입니다.");
        }
        return users.findByLoginId(loginId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 확인할 수 없습니다."));
    }

    public User requireWritable(String roomId, Principal principal) {
        User user = requireMember(roomId, principal);
        Room room = rooms.findById(roomId).orElse(null);
        if (room != null && room.type == com.individual.messenger.domain.RoomType.DIRECT && contacts != null) {
            for (String peer : room.members) if (!peer.equals(user.loginId) && contacts.blocked(user.loginId, peer))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "차단된 관계에서는 메시지나 통화를 시작할 수 없습니다.");
        } else if (room == null) {
            var server = servers.findByChannelsId(roomId).orElseThrow();
            var channel = server.channels.stream().filter(c -> c.id.equals(roomId)).findFirst().orElseThrow();
            if (!com.individual.messenger.service.ServerPolicy.write(server, channel, user.loginId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 채널에는 작성 권한이 없습니다.");
        }
        return user;
    }

    public User requireMember(String roomId, Principal principal) {
        User user = actor(principal);
        if (roomId == null || roomId.isBlank() || roomId.length() > 100 || roomId.contains("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효한 채팅방 ID가 필요합니다.");
        }
        Room room = rooms.findById(roomId).orElse(null);
        boolean allowed;
        if (room != null) {
            // IDs are case-sensitive like users.loginId. Never authorize a different account by lowercasing.
            allowed = room.members != null && room.members.contains(user.loginId);
        } else {
            allowed = servers.findByChannelsId(roomId)
                    .map(server -> server.channels.stream().filter(c -> c.id.equals(roomId))
                            .anyMatch(c -> com.individual.messenger.service.ServerPolicy.read(server, c, user.loginId))).orElse(false);
        }
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "채팅방에 접근할 권한이 없습니다.");
        return user;
    }
}
