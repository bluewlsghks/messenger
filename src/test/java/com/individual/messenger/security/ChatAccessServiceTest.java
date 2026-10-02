package com.individual.messenger.security;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.User;
import com.individual.messenger.repo.ChatServerRepository;
import com.individual.messenger.repo.RoomRepository;
import com.individual.messenger.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatAccessServiceTest {
    final RoomRepository rooms = mock(RoomRepository.class);
    final ChatServerRepository servers = mock(ChatServerRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ChatAccessService access = new ChatAccessService(rooms, servers, users);
    final Principal alice = () -> "alice";
    User user;
    @BeforeEach void setup() {
        user = new User(); user.loginId = "alice"; user.userName = "Alice";
        when(users.findByLoginId("alice")).thenReturn(Optional.of(user));
    }
    @Test void rejectsUnauthenticatedUser() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> access.actor(null)).getStatusCode().value());
        verifyNoInteractions(rooms, servers);
    }
    @Test void rejectsUnknownAccountAndReservedBot() {
        assertThrows(ResponseStatusException.class, () -> access.actor(() -> "missing"));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> access.actor(() -> "AI_BOT")).getStatusCode().value());
    }
    @Test void permitsRoomMember() {
        Room room = new Room(); room.members = List.of("alice", "bob");
        when(rooms.findById("room")).thenReturn(Optional.of(room));
        assertSame(user, access.requireMember("room", alice));
    }
    @Test void rejectsNonMemberWithoutFallingBackToChannel() {
        Room room = new Room(); room.members = List.of("bob");
        when(rooms.findById("room")).thenReturn(Optional.of(room));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> access.requireMember("room", alice)).getStatusCode().value());
        verifyNoInteractions(servers);
    }
    @Test void permitsServerMemberInChannel() {
        ChatServer server = new ChatServer(); server.members.add("alice");
        when(servers.findByChannelsId("channel")).thenReturn(Optional.of(server));
        assertSame(user, access.requireMember("channel", alice));
    }
    @Test void membershipIsRecheckedNotCached() {
        ChatServer server = new ChatServer(); server.members.add("alice");
        when(servers.findByChannelsId("channel")).thenReturn(Optional.of(server));
        access.requireMember("channel", alice); server.members.clear();
        assertThrows(ResponseStatusException.class, () -> access.requireMember("channel", alice));
    }
    @Test void doesNotMergeCaseSensitiveAccounts() {
        Room room = new Room(); room.members = List.of("Alice", "bob");
        when(rooms.findById("room")).thenReturn(Optional.of(room));
        assertThrows(ResponseStatusException.class, () -> access.requireMember("room", alice));
    }
    @Test void rejectsInvalidRoomId() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> access.requireMember("room/read", alice)).getStatusCode().value());
        verifyNoInteractions(rooms, servers);
    }
}
