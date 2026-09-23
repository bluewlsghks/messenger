package com.individual.messenger.service;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.Message;
import com.individual.messenger.repo.ChatServerRepository;
import com.individual.messenger.repo.ServerInviteRepository;
import com.individual.messenger.repo.MessageRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Uses only an isolated randomly named test database. Never points at MONGODB_URI. */
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
class MessengerMongoIntegrationTest {
    MongoClient client; MongoTemplate mongo; ChatServerService servers; MessageService messages;
    ChatServerRepository serverRepo; MessageRepository messageRepo; SimpMessagingTemplate messaging;
    @BeforeEach void setup() {
        client = MongoClients.create(System.getenv("MONGODB_TEST_URI"));
        mongo = new MongoTemplate(client, "messenger_test_" + UUID.randomUUID().toString().replace("-", ""));
        MongoRepositoryFactory factory = new MongoRepositoryFactory(mongo);
        serverRepo = factory.getRepository(ChatServerRepository.class);
        messageRepo = factory.getRepository(MessageRepository.class);
        servers = new ChatServerService(serverRepo, factory.getRepository(ServerInviteRepository.class), mongo);
        messaging = mock(SimpMessagingTemplate.class);
        messages = new MessageService(messageRepo, messaging, mongo);
    }
    @AfterEach void cleanup() {
        if (mongo != null) mongo.getDb().drop();
        if (client != null) client.close();
    }
    @Test void serverCreationAndJoiningAreConsistentAndIdempotent() {
        ChatServer server = servers.create("alice", "서버");
        assertEquals(server.id, serverRepo.findByChannelsId(server.channels.get(0).id).orElseThrow().id);
        var invite = servers.issueInvite(server.id, "alice");
        servers.join("bob", invite.code()); servers.join("bob", invite.code());
        assertEquals(List.of("alice", "bob"), serverRepo.findById(server.id).orElseThrow().members);
    }
    @Test void duplicateChannelCreationIsAtomicUnderConcurrency() throws Exception {
        ChatServer server = servers.create("alice", "서버");
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 8; i++) attempts.add(() -> {
            try { servers.createChannel(server.id, "alice", "동일채널"); return true; }
            catch (ResponseStatusException conflict) { assertEquals(409, conflict.getStatusCode().value()); return false; }
        });
        try (var executor = Executors.newFixedThreadPool(8)) {
            int created = 0; for (var result : executor.invokeAll(attempts)) if (result.get()) created++;
            assertEquals(1, created);
        }
        assertEquals(2, serverRepo.findById(server.id).orElseThrow().channels.size());
    }
    @Test void equalTimestampsDoNotLoseMessagesAcrossCursorPages() {
        Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) { Message m = new Message("room", "alice", "message " + i); m.createdAt = sameTime; messageRepo.save(m); }
        List<String> ids = new ArrayList<>(); Instant before = null; String beforeId = null;
        for (int page = 0; page < 3; page++) {
            List<Message> batch = messages.history("room", before, beforeId, 2);
            assertFalse(batch.isEmpty()); batch.forEach(m -> ids.add(m.id));
            before = batch.get(0).createdAt; beforeId = batch.get(0).id;
        }
        assertEquals(5, ids.size()); assertEquals(5, ids.stream().distinct().count());
    }
    @Test void readReceiptsCannotUpdateOrPublishIdsFromAnotherRoom() {
        Message allowed = messageRepo.save(new Message("room", "bob", "one"));
        Message other = messageRepo.save(new Message("other", "bob", "two"));
        messages.markRead("room", List.of(allowed.id, other.id), "alice");
        assertEquals(List.of("alice"), messageRepo.findById(allowed.id).orElseThrow().readBy);
        assertTrue(messageRepo.findById(other.id).orElseThrow().readBy.isEmpty());
        verify(messaging).convertAndSend(eq("/sub/chat/room/read"), eq(java.util.Map.of("messageIds", List.of(allowed.id), "readerId", "alice")));
    }
    @Test void persistenceProducesOneCanonicalBroadcast() {
        Message saved = messages.save("room", "alice", "Alice", "hello");
        assertTrue(messageRepo.existsById(saved.id));
        verify(messaging, times(1)).convertAndSend(eq("/sub/chat/room"), eq(saved));
        verifyNoMoreInteractions(messaging);
    }
}
