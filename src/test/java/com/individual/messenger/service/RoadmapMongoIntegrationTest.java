package com.individual.messenger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.*;
import com.individual.messenger.dto.*;
import com.individual.messenger.repository.*;
import com.individual.messenger.security.ChatAccessService;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real Mongo atomicity and shared-state checks; an isolated random database is always dropped afterwards. */
@EnabledIfEnvironmentVariable(named="MONGODB_TEST_URI", matches=".+")
class RoadmapMongoIntegrationTest {
    MongoClient client; MongoTemplate mongo; MongoRepositoryFactory factory;
    MessageRepository messageRepo; RoomRepository rooms; UserRepository users; ChatServerRepository serverRepo;
    ChatAccessService access; ContactService contacts; MessageService messages;
    ChatServerService servers; ServerAdministration admin; AttachmentService files;
    SessionService sessions; ObjectMapper json; CryptoService crypto;
    SimpMessagingTemplate broker; ConversationEvents events;
    Principal alice=()->"alice", bob=()->"bob", eve=()->"eve";
    @BeforeEach void setup() {
        client=MongoClients.create(System.getenv("MONGODB_TEST_URI"));
        mongo=new MongoTemplate(client,"messenger_roadmap_test_"+UUID.randomUUID().toString().replace("-",""));
        factory=new MongoRepositoryFactory(mongo);messageRepo=factory.getRepository(MessageRepository.class);
        rooms=factory.getRepository(RoomRepository.class);users=factory.getRepository(UserRepository.class);
        serverRepo=factory.getRepository(ChatServerRepository.class);
        for(String name:List.of("alice","bob","eve","dave","frank")){User user=new User();user.loginId=name;user.userName=name;users.insert(user);}
        Room room=Room.directOf("alice","bob");room.id="dm";rooms.insert(room);
        contacts=new ContactService(mongo,users);access=new ChatAccessService(rooms,serverRepo,users);
        ReflectionTestUtils.setField(access,"contacts",contacts);
        broker=mock(SimpMessagingTemplate.class);events=mock(ConversationEvents.class);
        messages=new MessageService(messageRepo,broker,mongo);
        servers=new ChatServerService(serverRepo,factory.getRepository(ServerInviteRepository.class),mongo);
        admin=new ServerAdministration(servers,mongo);sessions=new SessionService(mongo);
        files=new AttachmentService(new GridFsTemplate(mongo.getMongoDatabaseFactory(),mongo.getConverter()),mongo,access);
        crypto=new CryptoService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        json=new ObjectMapper().findAndRegisterModules();
        mongo.indexOps(Message.class).createIndex(new Index().on("senderId",Sort.Direction.ASC).on("clientRequestId",Sort.Direction.ASC)
                .unique().partial(PartialIndexFilter.of(Criteria.where("clientRequestId").type(2))));
    }
    @AfterEach void cleanup(){if(mongo!=null)mongo.getDb().drop();if(client!=null)client.close();}
    static <T> ObjectProvider<T> provider(T instance){ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(instance);return provider;}
    MessageOutbox outbox(){ObjectProvider<MessageProjection> projections=mock(ObjectProvider.class);when(projections.orderedStream()).thenAnswer(inv->Stream.empty());return new MessageOutbox(mongo,provider(events),provider(broker),projections);}
    MessageService.Stored store(String key,String content){return messages.store("dm","alice","Alice",content,key,null,null,List.of(),List.of());}
    void status(int expected,Runnable work){assertEquals(expected,assertThrows(ResponseStatusException.class,work::run).getStatusCode().value());}
    ChatServer community(){ChatServer s=servers.create("alice","공유 서버");var invitation=servers.issueInvite(s.id,"alice");servers.join("bob",invitation.code());servers.join("eve",invitation.code());return serverRepo.findById(s.id).orElseThrow();}

    @Test void concurrentTransportRetriesInsertExactlyOneMessage() throws Exception {
        String key=UUID.randomUUID().toString();List<Callable<MessageService.Stored>> tasks=new ArrayList<>();
        for(int i=0;i<12;i++)tasks.add(()->store(key,"once"));
        Set<String> ids=new HashSet<>();int created=0;
        try(var pool=Executors.newFixedThreadPool(6)){for(var future:pool.invokeAll(tasks)){var item=future.get();ids.add(item.message().id);if(item.created())created++;}}
        assertEquals(1,ids.size());assertEquals(1,created);assertEquals(1,messageRepo.count());
        assertTrue(messageRepo.findAll().getFirst().publicationPending);verifyNoInteractions(broker);
    }
    @Test void requestIdCannotBeReusedWithDifferentPayloadAndSurvivesEdit() {
        String key=UUID.randomUUID().toString();Message first=store(key,"original").message();
        status(409,()->store(key,"different"));
        mongo.updateFirst(Query.query(Criteria.where("id").is(first.id)),new Update().set("content","edited").inc("version",1),Message.class);
        var retry=store(key,"original");assertFalse(retry.created());assertEquals("edited",retry.message().content);assertEquals(1,messageRepo.count());
    }
    @Test void transportRetryOfReplyStillWorksAfterParentWasDeleted() {
        Message parent=store(UUID.randomUUID().toString(),"parent").message();
        ChatMessagingService chat=new ChatMessagingService(messages,access,mock(OpenAiService.class),Runnable::run);
        ReflectionTestUtils.setField(chat,"extras",new MessageExtras(mongo,access));
        var body=new MessageRequests.SendRequest("dm","reply",UUID.randomUUID().toString(),parent.id,List.of());
        Message reply=chat.send(body,alice);
        mongo.updateFirst(Query.query(Criteria.where("id").is(parent.id)),new Update().set("deletedAt",Instant.now()),Message.class);
        assertEquals(reply.id,chat.send(body,alice).id);assertEquals(2,messageRepo.count());
    }
    @Test void outboxPublicationRetriesAfterBrokerFailure() {
        Message saved=store(UUID.randomUUID().toString(),"event").message();MessageOutbox outbox=outbox();
        doThrow(new IllegalStateException("offline")).doNothing().when(events).publish(eq("MESSAGE_CREATED"),any());
        assertTrue(outbox.deliverOne());assertTrue(messageRepo.findById(saved.id).orElseThrow().publicationPending);
        verifyNoInteractions(broker);
        mongo.updateFirst(Query.query(Criteria.where("id").is(saved.id)),new Update().set("publishAfter",Instant.EPOCH),Message.class);
        assertTrue(outbox.deliverOne());assertFalse(messageRepo.findById(saved.id).orElseThrow().publicationPending);
        verify(broker).convertAndSend(eq("/topic/chat/dm"),argThat((Object value)->value instanceof Message m && m.id.equals(saved.id)));
    }
    @Test void outboxCannotAcknowledgeAnEditMadeDuringPublish() {
        Message saved=store(UUID.randomUUID().toString(),"old").message();MessageOutbox outbox=outbox();
        doAnswer(inv->{mongo.updateFirst(Query.query(Criteria.where("id").is(saved.id)),new Update().set("content","new")
                .inc("version",1).set("publicationPending",true),Message.class);return null;}).doNothing().when(events).publish(anyString(),any());
        outbox.deliverOne();assertTrue(messageRepo.findById(saved.id).orElseThrow().publicationPending);
        outbox.deliverOne();assertFalse(messageRepo.findById(saved.id).orElseThrow().publicationPending);
        assertEquals("new",messageRepo.findById(saved.id).orElseThrow().content);
    }
    @Test void twoOutboxWorkersCannotOwnTheSameSnapshot() throws Exception {
        store(UUID.randomUUID().toString(),"one");var first=outbox();var second=outbox();
        try(var pool=Executors.newFixedThreadPool(2)){var results=pool.<Boolean>invokeAll(List.of(first::deliverOne,second::deliverOne));
            assertEquals(1,results.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());}
        verify(events,times(1)).publish(eq("MESSAGE_CREATED"),any());
    }
    @Test void fullSyncTraversesMoreThanOneHundredMessagesAndIncludesDeletion() {
        List<Message> batch=new ArrayList<>();for(int i=0;i<205;i++)batch.add(new Message("dm","alice","m"+i));messageRepo.insert(batch);
        mongo.updateFirst(Query.query(Criteria.where("id").is(batch.get(0).id)),new Update().set("content","").set("deletedAt",Instant.now()).set("version",1),Message.class);
        Set<String> ids=new HashSet<>();String cursor=null;boolean deletion=false;
        for(int page=0;page<3;page++) {var result=messages.sync("dm",cursor,100);for(Message m:result){ids.add(m.id);deletion|=m.deletedAt!=null;}cursor=result.getLast().id;}
        assertEquals(205,ids.size());assertTrue(deletion);
    }
    @Test void replyAndMentionsAreRestrictedToTheSameConversation() {
        Message root=store(UUID.randomUUID().toString(),"root").message();MessageExtras extras=new MessageExtras(mongo,access);
        var result=extras.prepare("dm","alice","@bob @eve",root.id,List.of());assertEquals(root.id,result.threadId());assertEquals(List.of("bob"),result.mentions());
        assertThrows(IllegalArgumentException.class,()->extras.prepare("other","alice","reply",root.id,List.of()));
    }
    @Test void legacyReadEndpointUpdatesTheCanonicalReadModel() {
        Message a=messages.save("dm","bob","Bob","one"), b=messages.save("dm","bob","Bob","two");
        messages.markAllRead("dm","alice");assertTrue(messageRepo.findById(a.id).orElseThrow().readBy.contains("alice"));assertTrue(messageRepo.findById(b.id).orElseThrow().readBy.contains("alice"));
    }
    @Test void refreshRotationStoresOnlyHashesAndProvenReplayRevokesSession() throws Exception {
        var first=sessions.open("alice");var next=sessions.rotate(first.refreshToken());assertNotEquals(first.refreshToken(),next.refreshToken());
        String raw=mongo.getCollection("auth_sessions").find().first().toJson();assertFalse(raw.contains(first.refreshToken()));assertFalse(raw.contains(next.refreshToken()));
        assertTrue(sessions.active(next.sessionId(),"alice"));status(401,()->sessions.rotate(first.refreshToken()));assertFalse(sessions.active(next.sessionId(),"alice"));
        assertFalse(json.writeValueAsString(mongo.findById(first.sessionId(),AuthSession.class)).contains("currentHash"));
    }
    @Test void guessedRefreshSecretCannotRevokeAnotherSession() {
        var issued=sessions.open("alice");status(401,()->sessions.rotate(issued.sessionId()+"."+"A".repeat(43)));
        assertTrue(sessions.active(issued.sessionId(),"alice"));sessions.revokeOwned(issued.sessionId(),"bob");assertTrue(sessions.active(issued.sessionId(),"alice"));
        sessions.revokeOwned(issued.sessionId(),"alice");assertFalse(sessions.active(issued.sessionId(),"alice"));
    }
    @Test void concurrentRefreshUseDetectsReplayInsteadOfIssuingTwoValidDescendants() throws Exception {
        var issued=sessions.open("alice");Callable<Boolean> rotate=()->{try{sessions.rotate(issued.refreshToken());return true;}catch(ResponseStatusException rejected){return false;}};
        try(var pool=Executors.newFixedThreadPool(2)){int ok=0;for(var result:pool.invokeAll(List.of(rotate,rotate)))if(result.get())ok++;assertEquals(1,ok);}
        assertFalse(sessions.active(issued.sessionId(),"alice"));
    }
    @Test void friendshipRequiresRecipientConsentAndCannotBeSelfAccepted() {
        contacts.request("alice","bob");assertTrue(contacts.friends("alice",List.of()).isEmpty());status(403,()->contacts.respond("alice","bob","ACCEPT"));
        contacts.respond("bob","alice","ACCEPT");assertEquals(Set.of("bob"),contacts.friends("alice",List.of()));assertEquals(Set.of("alice"),contacts.friends("bob",List.of()));
    }
    @Test void blockingPreventsDmWritesAndRequestsWithoutErasingHistory() {
        contacts.block("bob","alice",true);status(403,()->access.requireWritable("dm",alice));assertNotNull(access.requireMember("dm",alice));
        status(403,()->contacts.request("alice","bob"));contacts.block("bob","alice",false);assertNotNull(access.requireWritable("dm",alice));
        assertTrue(contacts.friends("alice",List.of()).isEmpty());
    }
    @Test void privateChannelAndReadOnlyChannelAreEnforcedAtServerBoundary() {
        ChatServer server=community();String channel=server.channels.getFirst().id;
        admin.permissions(server.id,"alice",channel,List.of("bob"),List.of());
        assertNotNull(access.requireMember(channel,bob));status(403,()->access.requireWritable(channel,bob));status(403,()->access.requireMember(channel,eve));
        assertNotNull(access.requireWritable(channel,alice));
    }
    @Test void inviteRevocationAndBanningSurviveRejoinAttempts() {
        ChatServer server=community();var before=servers.issueInvite(server.id,"alice");admin.command(server.id,"alice","REVOKE_INVITES",null);
        assertThrows(ResponseStatusException.class,()->servers.join("dave",before.code()));
        var next=servers.issueInvite(server.id,"alice");admin.command(server.id,"alice","BAN","bob");
        assertThrows(ResponseStatusException.class,()->servers.join("bob",next.code()));status(403,()->access.requireMember(server.channels.getFirst().id,bob));
    }
    @Test void moderatorsCannotTakeOwnershipOrKickTheOwner() {
        ChatServer server=community();admin.command(server.id,"alice","PROMOTE","bob");
        status(403,()->admin.command(server.id,"bob","TRANSFER","eve"));status(403,()->admin.command(server.id,"bob","KICK","alice"));
        admin.command(server.id,"bob","KICK","eve");assertFalse(serverRepo.findById(server.id).orElseThrow().members.contains("eve"));
    }
    @Test void ownershipTransferThenLeaveAndSoftDeletePreserveDocumentsButRevokeAccess() {
        ChatServer server=community();admin.command(server.id,"alice","TRANSFER","bob");admin.command(server.id,"alice","LEAVE",null);
        status(403,()->access.requireMember(server.channels.getFirst().id,alice));admin.command(server.id,"bob","DELETE",null);
        assertTrue(serverRepo.findById(server.id).orElseThrow().deleted);status(403,()->access.requireMember(server.channels.getFirst().id,bob));
    }
    @Test void attachmentsRequireOwnerAndRoomAndMessageVisibility() throws Exception {
        var file=files.upload(alice,"dm",new MockMultipartFile("file","../unsafe.html","text/html","test text".getBytes()));
        assertTrue(file.name().endsWith(".txt"));assertFalse(file.name().contains("/"));assertNotNull(files.require(alice,file.id()));status(403,()->files.require(bob,file.id()));
        MessageExtras extras=new MessageExtras(mongo,access);assertThrows(IllegalArgumentException.class,()->extras.prepare("dm","bob","x",null,List.of(file.id())));
        extras.prepare("dm","alice","x",null,List.of(file.id()));
        Message saved=messages.store("dm","alice","Alice","attachment",UUID.randomUUID().toString(),null,null,List.of(file.id()),List.of()).message();
        assertEquals("test text",new String(files.stream(files.require(bob,file.id())).readAllBytes()));
        mongo.updateFirst(Query.query(Criteria.where("id").is(saved.id)),new Update().set("deletedAt",Instant.now()),Message.class);
        status(403,()->files.require(bob,file.id()));
    }
    @Test void claimingAttachmentPreventsConcurrentPendingDeletion() throws Exception {
        var file=files.upload(alice,"dm",new MockMultipartFile("file","t.txt","text/plain","hello".getBytes()));
        new MessageExtras(mongo,access).prepare("dm","alice","hello",null,List.of(file.id()));
        status(409,()->files.deletePending(alice,file.id()));assertNotNull(files.require(alice,file.id()));
    }
    @Test void cancelledUnsentAttachmentReleasesItsQuotaExactlyOnce() throws Exception {
        var file=files.upload(alice,"dm",new MockMultipartFile("file","t.txt","text/plain","hello".getBytes()));files.deletePending(alice,file.id());
        assertEquals(0,((Number)mongo.findById("alice",Document.class,"upload_quotas").get("bytes")).longValue());
        status(404,()->files.deletePending(alice,file.id()));
    }
    @Test void uploadQuotaAndFileSignaturesAreEnforcedBeforeGridFsWrite() {
        mongo.insert(new Document("_id","alice").append("bytes",100L*1024*1024),"upload_quotas");
        status(413,()->{try{files.upload(alice,"dm",new MockMultipartFile("file","t.txt","text/plain","hello".getBytes()));}catch(java.io.IOException e){throw new RuntimeException(e);}});
        assertEquals(0,mongo.getCollection("fs.files").countDocuments());assertThrows(IllegalArgumentException.class,()->AttachmentService.sniff(new byte[]{0,1,2,3}));
    }
    MediaRegistry registry(VoiceEvents voice){return new MediaRegistry(new EphemeralStateStore(mongo,json,crypto),provider(voice));}
    @Test void mediaStateIsEncryptedAndTwoInstancesShareOneUserReservation() {
        VoiceEvents voice=mock(VoiceEvents.class);when(voice.online(anyString())).thenReturn(true);
        var one=registry(voice);var two=registry(voice);
        var first=new VoiceCallService(access,rooms,voice,one);var second=new VoiceCallService(access,rooms,voice,two);
        UUID id=UUID.randomUUID(),tab=UUID.randomUUID();first.start(alice,id,tab,"dm");
        status(409,()->second.start(bob,UUID.randomUUID(),UUID.randomUUID(),"dm"));assertEquals(id,second.current(bob).id());
        String raw=mongo.getCollection("media_state").find().first().toJson();assertFalse(raw.contains("alice"));assertFalse(raw.contains("bob"));assertFalse(raw.contains(id.toString()));
        first.command(alice,id,new VoiceCallDtos.Command(tab,VoiceCallDtos.Action.END,null,null));one.drain();
        assertNull(second.current(alice));assertEquals(1,second.history(bob).size());assertTrue(second.history(eve).isEmpty());
    }
    @Test void concurrentMediaReservationsHaveOneWinnerAcrossInstances() throws Exception {
        VoiceEvents voice=mock(VoiceEvents.class);when(voice.online(anyString())).thenReturn(true);
        var first=new VoiceCallService(access,rooms,voice,registry(voice));var second=new VoiceCallService(access,rooms,voice,registry(voice));
        Callable<Boolean> a=()->{try{first.start(alice,UUID.randomUUID(),UUID.randomUUID(),"dm");return true;}catch(ResponseStatusException e){return false;}};
        Callable<Boolean> b=()->{try{second.start(bob,UUID.randomUUID(),UUID.randomUUID(),"dm");return true;}catch(ResponseStatusException e){return false;}};
        try(var pool=Executors.newFixedThreadPool(2)){int wins=0;for(var result:pool.invokeAll(List.of(a,b)))if(result.get())wins++;assertEquals(1,wins);}
    }
    @Test void conferenceCapacityTabPinningAndDmExclusionUseSharedState() {
        Room group=new Room();group.id="group";group.type=RoomType.GROUP;group.members=List.of("alice","bob","eve","dave","frank");rooms.insert(group);
        VoiceEvents voice=mock(VoiceEvents.class);when(voice.online(anyString())).thenReturn(true);var registry=registry(voice);
        ConferenceService conference=new ConferenceService(registry,access,rooms);UUID tab=UUID.randomUUID();conference.join(alice,"group",tab);
        status(409,()->conference.join(alice,"group",UUID.randomUUID()));
        for(String member:List.of("bob","eve","dave"))conference.join(()->member,"group",UUID.randomUUID());
        status(409,()->conference.join(()->"frank","group",UUID.randomUUID()));
        status(409,()->new VoiceCallService(access,rooms,voice,registry).start(alice,UUID.randomUUID(),UUID.randomUUID(),"dm"));
        assertEquals(4,conference.view(alice,"group").members().size());
        conference.command(alice,"group",new ConferenceDtos.Command(tab,ConferenceDtos.Action.LEAVE,null,null,null));assertEquals(3,conference.view(bob,"group").members().size());
    }
    @Test void rateLimitIsAtomicAcrossTwoServices() throws Exception {
        RequestBudget first=new RequestBudget(mongo),second=new RequestBudget(mongo);List<Callable<Boolean>> attempts=new ArrayList<>();
        for(int i=0;i<20;i++){RequestBudget service=i%2==0?first:second;attempts.add(()->{try{service.consume("alice","test",5,3600);return true;}catch(ResponseStatusException rejected){assertEquals(429,rejected.getStatusCode().value());return false;}});}
        try(var pool=Executors.newFixedThreadPool(4)){int allowed=0;for(var result:pool.invokeAll(attempts))if(result.get())allowed++;assertEquals(5,allowed);}
    }
}
