package com.individual.messenger.service;

import com.individual.messenger.domain.*;
import com.individual.messenger.dto.CollaborationDtos.*;
import com.individual.messenger.repository.*;
import com.individual.messenger.security.ChatAccessService;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real single-document atomicity; each test owns a disposable DB, never the configured user DB. */
@EnabledIfEnvironmentVariable(named="MONGODB_TEST_URI", matches=".+")
class MessageCollaborationMongoTest {
    MongoClient client; MongoTemplate mongo; MessageRepository messageRepo; RoomRepository rooms;
    ChatServerRepository servers; MessageService messages; MessageCollaborationRepository repository;
    MessageJournalRepository journal; MessageSyncService sync; MessageCollaborationService collaboration;
    ChatAccessService access; SimpMessagingTemplate broker;
    Principal alice=()->"alice", bob=()->"bob", eve=()->"eve";
    @BeforeEach void setup() {
        client=MongoClients.create(System.getenv("MONGODB_TEST_URI"));
        mongo=new MongoTemplate(client,"messenger_collaboration_test_"+UUID.randomUUID().toString().replace("-",""));
        var factory=new MongoRepositoryFactory(mongo);
        messageRepo=factory.getRepository(MessageRepository.class);rooms=factory.getRepository(RoomRepository.class);
        servers=factory.getRepository(ChatServerRepository.class);var users=factory.getRepository(UserRepository.class);
        for(String name:List.of("alice","bob","eve")){User u=new User();u.loginId=name;u.userName=name;users.insert(u);}
        Room room=Room.directOf("alice","bob");room.id="dm";rooms.insert(room);
        Room other=Room.directOf("alice","eve");other.id="other";rooms.insert(other);
        access=new ChatAccessService(rooms,servers,users);broker=mock(SimpMessagingTemplate.class);
        messages=new MessageService(messageRepo,broker,mongo);
        repository=new MessageCollaborationRepository(mongo);journal=new MessageJournalRepository(mongo);
        sync=new MessageSyncService(journal,repository,messages,access);
        collaboration=new MessageCollaborationService(repository,access,rooms,servers);
    }
    @AfterEach void cleanup(){if(mongo!=null)mongo.getDb().drop();if(client!=null)client.close();}
    Message message(String text){return messages.save("dm","alice","Alice",text);}
    void status(int code,Runnable run){assertEquals(code,assertThrows(ResponseStatusException.class,run::run).getStatusCode().value());}
    static <T> ObjectProvider<T> provider(T item){var p=mock(ObjectProvider.class);when(p.getObject()).thenReturn(item);return p;}
    @Test void duplicateConcurrentReactionsIncrementOnlyOnce() throws Exception {
        var m=message("react");List<Callable<Message>> jobs=IntStream.range(0,24).mapToObj(i->(Callable<Message>)()->collaboration.reaction(bob,"dm",m.id,"like",true)).toList();
        try(var pool=Executors.newFixedThreadPool(6)){for(var result:pool.invokeAll(jobs))assertEquals(List.of("bob"),result.get().reactions.get("like"));}
        var saved=messageRepo.findById(m.id).orElseThrow();assertEquals(1,saved.version);assertTrue(saved.publicationPending);
    }
    @Test void reactionRemovalAndPinAreDesiredStateIdempotent() {
        var m=message("states");collaboration.reaction(bob,"dm",m.id,"heart",true);
        collaboration.reaction(bob,"dm",m.id,"heart",false);var absent=collaboration.reaction(bob,"dm",m.id,"heart",false);
        assertEquals(2,absent.version);assertTrue(absent.reactions.get("heart").isEmpty());
        collaboration.pin(alice,"dm",m.id,true);var pin=collaboration.pin(bob,"dm",m.id,true);
        assertEquals(3,pin.version);assertEquals("alice",pin.pinnedBy);
        collaboration.pin(bob,"dm",m.id,false);assertEquals(4,collaboration.pin(bob,"dm",m.id,false).version);
    }
    @Test void reactorsAreUniquePerEmojiAndActor() {
        var m=message("people");collaboration.reaction(alice,"dm",m.id,"like",true);
        collaboration.reaction(bob,"dm",m.id,"like",true);collaboration.reaction(bob,"dm",m.id,"heart",true);
        var saved=messageRepo.findById(m.id).orElseThrow();assertEquals(Set.of("alice","bob"),new HashSet<>(saved.reactions.get("like")));
        assertEquals(List.of("bob"),saved.reactions.get("heart"));
    }
    @Test void reactionKeysAndCrossRoomIdsAreValidated() {
        var m=message("secret");status(403,()->collaboration.reaction(eve,"dm",m.id,"like",true));
        status(404,()->collaboration.reaction(alice,"other",m.id,"like",true));
        assertThrows(IllegalArgumentException.class,()->collaboration.reaction(alice,"dm",m.id,"$set.bad",true));
        assertThrows(IllegalArgumentException.class,()->collaboration.pin(alice,"dm","invalid",true));
        assertEquals(0,messageRepo.findById(m.id).orElseThrow().version);
    }
    @Test void deletingMessageClearsSharedMetadataAndRejectsFurtherChanges() {
        var m=message("delete");collaboration.reaction(bob,"dm",m.id,"eyes",true);var pinned=collaboration.pin(alice,"dm",m.id,true);
        var deleted=new MessageActionRepository(mongo).change("dm",m.id,"alice",pinned.version,null,Instant.now());
        assertNotNull(deleted);assertFalse(deleted.pinned);assertTrue(deleted.reactions.isEmpty());assertTrue(deleted.publicationPending);
        status(404,()->collaboration.pin(bob,"dm",m.id,true));status(404,()->collaboration.reaction(bob,"dm",m.id,"like",true));
        assertTrue(collaboration.pins(bob,"dm",null,100).items().isEmpty());
    }
    @Test void channelPinRequiresManagerAndCurrentAccess() {
        ChatServer server=new ChatServer();server.ownerId="alice";server.name="channel";server.members=new ArrayList<>(List.of("alice","bob","eve"));
        var channel=new ChatServer.TextChannel("general");server.channels.add(channel);server=servers.insert(server);
        var m=messages.save(channel.id,"alice","Alice","channel pin");
        assertFalse(collaboration.capabilities(bob,channel.id).canPin());status(403,()->collaboration.pin(bob,channel.id,m.id,true));
        server.moderators.add("bob");servers.save(server);assertTrue(collaboration.pin(bob,channel.id,m.id,true).pinned);
        server.members.remove("bob");servers.save(server);status(403,()->collaboration.pins(bob,channel.id,null,50));
    }
    @Test void readonlyChannelCannotReactButCanBookmark() {
        ChatServer server=new ChatServer();server.ownerId="alice";server.name="read";server.members=new ArrayList<>(List.of("alice","bob"));
        var channel=new ChatServer.TextChannel("readonly");channel.writers=new ArrayList<>();server.channels.add(channel);servers.insert(server);
        var m=messages.save(channel.id,"alice","Alice","read");assertFalse(collaboration.capabilities(bob,channel.id).canReact());
        status(403,()->collaboration.reaction(bob,channel.id,m.id,"like",true));collaboration.bookmark(bob,channel.id,m.id,true);
        assertEquals(m.id,collaboration.bookmarks(bob,channel.id,null,10).items().getFirst().id);
    }
    @Test void bookmarksArePrivateIdempotentReferencesToCurrentContent() throws Exception {
        var m=message("before");collaboration.bookmark(alice,"dm",m.id,true);collaboration.bookmark(alice,"dm",m.id,true);
        assertEquals(1,mongo.getCollection("message_bookmarks").countDocuments());assertTrue(collaboration.bookmarks(bob,"dm",null,50).items().isEmpty());
        new MessageActionRepository(mongo).change("dm",m.id,"alice",0,"after",Instant.now());
        assertEquals("after",collaboration.bookmarks(alice,"dm",null,50).items().getFirst().content);
        assertEquals(List.of(m.id),collaboration.saved(alice,"dm",List.of(m.id)).ids());assertTrue(collaboration.saved(bob,"dm",List.of(m.id)).ids().isEmpty());
        var stored=mongo.getCollection("message_bookmarks").find().first();assertFalse(stored.containsKey("content"));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(messageRepo.findById(m.id).orElseThrow());
        assertFalse(json.toLowerCase().contains("bookmark"));
    }
    @Test void deletedBookmarkRowsAdvanceCursorRatherThanHidingLaterPages() {
        var first=message("old");var second=message("live");collaboration.bookmark(alice,"dm",first.id,true);collaboration.bookmark(alice,"dm",second.id,true);
        new MessageActionRepository(mongo).change("dm",first.id,"alice",0,null,Instant.now());
        var empty=collaboration.bookmarks(alice,"dm",null,1);assertTrue(empty.items().isEmpty());assertTrue(empty.hasMore());assertEquals(first.id,empty.next());
        var next=collaboration.bookmarks(alice,"dm",empty.next(),1);assertEquals(second.id,next.items().getFirst().id);assertFalse(next.hasMore());
        collaboration.bookmark(alice,"dm",first.id,false);assertTrue(collaboration.saved(alice,"dm",List.of(first.id)).ids().isEmpty());
    }
    @Test void revokedMemberCannotReadSavedMessagesOrChanges() {
        var m=message("private");collaboration.bookmark(bob,"dm",m.id,true);String cursor=sync.changes(bob,"dm",null,100).cursor();
        Room room=rooms.findById("dm").orElseThrow();room.members=new ArrayList<>(List.of("alice"));rooms.save(room);
        status(403,()->collaboration.bookmarks(bob,"dm",null,100));status(403,()->collaboration.saved(bob,"dm",List.of(m.id)));
        status(403,()->sync.changes(bob,"dm",cursor,100));status(403,()->collaboration.snapshots(bob,"dm",List.of(m.id)));
    }
    @Test void journalSequenceAndArrayAppendAreAtomicAcrossWorkers() throws Exception {
        List<String> ids=IntStream.range(0,120).mapToObj(i->new ObjectId().toHexString()).toList();
        try(var pool=Executors.newFixedThreadPool(8)){var jobs=ids.stream().map(id->(Callable<Void>)()->{journal.append("dm",id);return null;}).toList();for(var result:pool.invokeAll(jobs))result.get();}
        var snapshot=journal.snapshot("dm");assertEquals(120,snapshot.sequence());assertEquals(new HashSet<>(ids),new HashSet<>(snapshot.ids()));assertEquals(120,snapshot.ids().size());
    }
    @Test void retentionOverflowAndWrongEpochResetToBoundedHistory() {
        var old=sync.changes(alice,"dm",null,100);var m=message("current");
        for(int i=0;i<1002;i++)journal.append("dm",m.id);
        assertEquals(1000,journal.snapshot("dm").ids().size());assertTrue(sync.changes(alice,"dm",old.cursor(),100).reset());
        String wrong=UUID.randomUUID()+":0";assertTrue(sync.changes(alice,"dm",wrong,100).reset());
        var current=sync.changes(alice,"dm",null,100);assertEquals(1,current.items().size());assertTrue(sync.changes(alice,"dm",current.cursor(),100).items().isEmpty());
    }
    @Test void changesRecoverOldEditsDeletionsAndDuplicateInvalidations() {
        var m=message("old");sync.publish("MESSAGE_CREATED",m);var base=sync.changes(alice,"dm",null,100);
        var edited=new MessageActionRepository(mongo).change("dm",m.id,"alice",0,"new",Instant.now());sync.publish("MESSAGE_UPDATED",edited);sync.publish("MESSAGE_UPDATED",edited);
        var first=sync.changes(alice,"dm",base.cursor(),1);assertEquals("new",first.items().getFirst().content);assertTrue(first.hasMore());
        var second=sync.changes(alice,"dm",first.cursor(),1);assertFalse(second.hasMore());assertNotEquals(first.cursor(),second.cursor());
        var deleted=new MessageActionRepository(mongo).change("dm",m.id,"alice",1,null,Instant.now());sync.publish("MESSAGE_DELETED",deleted);
        assertNotNull(sync.changes(alice,"dm",second.cursor(),100).items().getFirst().deletedAt);
    }
    @Test void bootstrapWatermarkIsCapturedBeforeHistoryRead() {
        var m=message("race");MessageService history=mock(MessageService.class);
        when(history.history("dm",null,null,100)).thenAnswer(inv->{journal.append("dm",m.id);return List.of();});
        var service=new MessageSyncService(journal,repository,history,access);var first=service.changes(alice,"dm",null,100);
        assertTrue(first.cursor().endsWith(":0"));assertEquals(m.id,service.changes(alice,"dm",first.cursor(),100).items().getFirst().id);
    }
    @Test void projectionFailureKeepsOutboxPendingForRetry() {
        var m=message("retry");var failing=spy(journal);doThrow(new IllegalStateException("simulated failure")).doCallRealMethod().when(failing).append("dm",m.id);
        var projection=new MessageSyncService(failing,repository,messages,access);
        ObjectProvider<MessageProjection> projections=mock(ObjectProvider.class);when(projections.orderedStream()).thenAnswer(inv->Stream.of(projection));
        var outbox=new MessageOutbox(mongo,provider(mock(ConversationEvents.class)),provider(broker),projections);
        outbox.deliverOne();assertTrue(messageRepo.findById(m.id).orElseThrow().publicationPending);
        mongo.updateFirst(Query.query(Criteria.where("id").is(m.id)),new Update().set("publishAfter",Instant.EPOCH),Message.class);
        outbox.deliverOne();assertFalse(messageRepo.findById(m.id).orElseThrow().publicationPending);assertEquals(List.of(m.id),journal.snapshot("dm").ids());
    }
    @Test void cursorsAndSnapshotLimitsAreValidated() {
        assertThrows(IllegalArgumentException.class,()->sync.changes(alice,"dm","garbage",100));
        assertThrows(IllegalArgumentException.class,()->sync.changes(alice,"dm",UUID.randomUUID()+":9999999999999999999",100));
        assertThrows(IllegalArgumentException.class,()->sync.changes(alice,"dm",null,101));
        assertThrows(IllegalArgumentException.class,()->collaboration.snapshots(alice,"dm",Collections.nCopies(101,new ObjectId().toHexString())));
        assertTrue(sync.changes(alice,"other",sync.changes(alice,"dm",null,100).cursor(),100).reset());
    }
    @Test void snapshotsRefreshReadReceiptsWithoutChangingMessageVersion() {
        var m=message("read");messages.markAllRead("dm","bob");var current=collaboration.snapshots(alice,"dm",List.of(m.id)).getFirst();
        assertEquals(List.of("bob"),current.readBy);assertEquals(0,current.version);
        assertTrue(collaboration.snapshots(alice,"other",List.of(m.id)).isEmpty());
    }
    @Test void reactorLimitIsCheckedInTheAtomicUpdate() {
        var m=message("capacity");List<String> people=IntStream.range(0,1000).mapToObj(i->"fixture"+i).toList();
        mongo.updateFirst(Query.query(Criteria.where("id").is(m.id)),new Update().set("reactions.like",people),Message.class);
        status(409,()->collaboration.reaction(bob,"dm",m.id,"like",true));assertEquals(1000,messageRepo.findById(m.id).orElseThrow().reactions.get("like").size());
    }
}
