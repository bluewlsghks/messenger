package com.individual.messenger.service;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.ServerInvite;
import com.individual.messenger.repo.ChatServerRepository;
import com.individual.messenger.repo.ServerInviteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatServerServiceTest {
    final ChatServerRepository servers = mock(ChatServerRepository.class);
    final ServerInviteRepository invites = mock(ServerInviteRepository.class);
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final ChatServerService service = new ChatServerService(servers, invites, mongo);
    ChatServer ownerServer() {
        ChatServer server = new ChatServer(); server.id = "server"; server.ownerId = "alice";
        server.members.add("alice"); server.members.add("bob");
        when(servers.findById("server")).thenReturn(Optional.of(server)); return server;
    }
    @Test void creationIncludesOwnerAndDefaultChannelInOneInsert() {
        when(servers.insert(any(ChatServer.class))).thenAnswer(call -> call.getArgument(0));
        ChatServer server = service.create("alice", "  개발 서버  ");
        assertEquals("개발 서버", server.name); assertEquals("alice", server.ownerId);
        assertEquals(java.util.List.of("alice"), server.members);
        assertEquals("일반", server.channels.get(0).name);
        verify(servers).insert(server); verifyNoInteractions(mongo);
    }
    @Test void validatesNamesBeforePersistence() {
        for (String name : new String[]{"", " ", "x".repeat(81), "hello\nworld"}) {
            assertThrows(ResponseStatusException.class, () -> service.create("alice", name));
        }
        verifyNoInteractions(servers);
    }
    @Test void ordinaryMemberCannotCreateChannelOrInvite() {
        ownerServer();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.createChannel("server", "bob", "general")).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.issueInvite("server", "bob"));
        verifyNoInteractions(mongo, invites);
    }
    @Test void inviteCodeIsRandomAndOnlyDigestIsPersisted() {
        ownerServer();
        var first = service.issueInvite("server", "alice"); var second = service.issueInvite("server", "alice");
        assertEquals(43, first.code().length()); assertNotEquals(first.code(), second.code());
        ArgumentCaptor<ServerInvite> captor = ArgumentCaptor.forClass(ServerInvite.class);
        verify(invites, times(2)).insert(captor.capture());
        assertEquals(ChatServerService.digest(first.code()), captor.getAllValues().get(0).id);
        assertNotEquals(first.code(), captor.getAllValues().get(0).id);
        assertTrue(first.expiresAt().isAfter(Instant.now().plusSeconds(6 * 86400)));
    }
    @Test void expiredInviteCannotJoinEvenBeforeTtlCleanup() {
        String code = "a".repeat(43); ServerInvite invite = new ServerInvite();
        invite.serverId = "server"; invite.expiresAt = Instant.now().minusSeconds(1);
        when(invites.findById(ChatServerService.digest(code))).thenReturn(Optional.of(invite));
        assertThrows(ResponseStatusException.class, () -> service.join("bob", code));
        verifyNoInteractions(mongo);
    }
    @Test void invalidInviteFailsWithoutDatabaseMutation() {
        assertThrows(ResponseStatusException.class, () -> service.join("bob", "bad"));
        verifyNoInteractions(invites, mongo);
    }
}
