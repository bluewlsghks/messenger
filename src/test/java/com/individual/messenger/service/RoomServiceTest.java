package com.individual.messenger.service;

import com.individual.messenger.domain.Room;
import com.individual.messenger.domain.RoomType;
import com.individual.messenger.repo.RoomRepository;
import com.individual.messenger.repo.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RoomServiceTest {
    final RoomRepository rooms = mock(RoomRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final RoomService service = new RoomService(rooms, users);
    void permitUsers() { when(users.existsByLoginId(anyString())).thenReturn(true); when(rooms.save(any(Room.class))).thenAnswer(call -> call.getArgument(0)); }
    @Test void newDmPreservesCaseSensitiveIds() {
        permitUsers(); Room room = service.createOrGetDirect("Alice", "alice");
        assertEquals(List.of("Alice", "alice"), room.members);
        assertNotEquals(Room.directKey("Alice", "bob"), Room.directKey("alice", "bob"));
    }
    @Test void keyEncodingCannotCollideOnSeparators() {
        assertNotEquals(Room.directKey("a#b", "c"), Room.directKey("a", "b#c"));
        assertEquals(Room.directKey("bob", "alice"), Room.directKey("alice", "bob"));
    }
    @Test void reusesMatchingLegacyDmWithoutRewritingIt() {
        permitUsers(); Room legacy = new Room(); legacy.type = RoomType.DIRECT;
        legacy.members = List.of("alice", "bob"); legacy.membersKey = "alice#bob";
        when(rooms.findByTypeAndMembersKey("DIRECT", "alice#bob")).thenReturn(Optional.of(legacy));
        assertSame(legacy, service.createOrGetDirect("alice", "bob"));
        verify(rooms, never()).save(any(Room.class));
    }
    @Test void neverReusesLegacyDmOwnedByDifferentCaseSensitiveAccounts() {
        permitUsers(); Room legacy = new Room(); legacy.type = RoomType.DIRECT; legacy.members = List.of("alice", "bob");
        when(rooms.findByTypeAndMembersKey("DIRECT", "alice#bob")).thenReturn(Optional.of(legacy));
        Room created = service.createOrGetDirect("Alice", "bob");
        assertNotSame(legacy, created); assertTrue(created.members.contains("Alice"));
    }
    @Test void groupsGetIndependentNonNullKeysAndDistinctMembers() {
        Room first = Room.groupOf(List.of("alice", "bob", "cat", "alice"));
        Room second = Room.groupOf(List.of("alice", "bob", "cat"));
        assertEquals(3, first.members.size()); assertNotNull(first.membersKey); assertNotEquals(first.membersKey, second.membersKey);
    }
    @Test void invalidGroupAndUnknownPeerAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Room.groupOf(List.of("alice", "alice", "bob")));
        assertThrows(IllegalArgumentException.class, () -> service.createOrGetDirect("alice", "missing"));
        verify(rooms, never()).save(any(Room.class));
    }
}
