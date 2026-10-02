package com.individual.messenger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.individual.messenger.domain.User;
import com.individual.messenger.security.JwtUtil;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.openai.enabled=false", "app.crypto.aesKeyBase64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.jwt.secretBase64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@EnabledIfEnvironmentVariable(named="MONGODB_TEST_URI",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceHttpIntegrationTest {
    static final String DATABASE="messenger_workspace_test_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){registry.add("spring.data.mongodb.uri",()->System.getenv("MONGODB_TEST_URI"));registry.add("spring.data.mongodb.database",()->DATABASE);}
    @Autowired MongoTemplate mongo;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    final HttpClient client=HttpClient.newHttpClient();
    @AfterAll void cleanup(){mongo.getDb().drop();}
    String user(String prefix){String id=prefix+UUID.randomUUID().toString().substring(0,8);User user=new User();user.loginId=id;user.userName="이름-"+id;mongo.save(user);return id;}
    HttpResponse<String> request(String who,String method,String path,Object body)throws Exception{var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");if(who!=null)builder.header("Authorization","Bearer "+jwt.createToken(who,Map.of()));builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    JsonNode json(HttpResponse<String> result)throws Exception{return mapper.readTree(result.body());}
    @Test void stringPrincipalWorksForFriendListAndMutualAddRemove()throws Exception{
        String alice=user("friend-a"),bob=user("friend-b");
        var empty=request(alice,"GET","/api/friends",null);assertEquals(200,empty.statusCode(),empty.body());assertEquals(0,json(empty).size());
        assertEquals(204,request(alice,"POST","/api/friends",Map.of("friendId",bob)).statusCode());
        assertEquals(204,request(alice,"POST","/api/friends",Map.of("friendId",bob)).statusCode());
        var list=json(request(alice,"GET","/api/friends",null));assertEquals(1,list.size());assertEquals("이름-"+bob,list.get(0).get("userName").asText());
        assertEquals(alice,json(request(bob,"GET","/api/friends",null)).get(0).get("userId").asText());
        assertEquals(204,request(alice,"DELETE","/api/friends?friendId="+bob,null).statusCode());assertEquals(0,json(request(bob,"GET","/api/friends",null)).size());
    }
    @Test void friendsRejectMissingSelfAndAnonymousAccounts()throws Exception{String alice=user("invalid-a");assertEquals(401,request(null,"GET","/api/friends",null).statusCode());assertEquals(400,request(alice,"POST","/api/friends",Map.of("friendId",alice)).statusCode());assertEquals(404,request(alice,"POST","/api/friends",Map.of("friendId","missing-user")).statusCode());}
    @Test void legacyFriendDuplicatesAndBadOptionalDatesDoNotBreakListing()throws Exception{
        String alice=user("legacy-a"),bob=user("legacy-b");for(int i=0;i<2;i++)mongo.getCollection("friends").insertOne(new Document("ownerId",alice).append("friendId",bob).append("createdAt","old-non-date-value"));
        var response=request(alice,"GET","/api/friends",null);assertEquals(200,response.statusCode(),response.body());assertEquals(1,json(response).size());
    }
    @Test void phoneLessProfileIsSerializableAndRenamePersists()throws Exception{
        String alice=user("profile-a");var profile=request(alice,"GET","/api/users/me",null);assertEquals(200,profile.statusCode(),profile.body());assertTrue(json(profile).get("phoneNumber").isNull());
        var updated=request(alice,"PATCH","/api/users/me",Map.of("userName","새 이름"));assertEquals(200,updated.statusCode(),updated.body());assertEquals("새 이름",json(request(alice,"GET","/api/users/me",null)).get("userName").asText());
    }
    @Test void editsAreAuthorOnlyVersionCheckedAndDeletionRemovesContent()throws Exception{
        String alice=user("edit-a"),bob=user("edit-b");String room=json(request(alice,"POST","/api/rooms/dm",Map.of("peerId",bob))).get("id").asText();
        var message=json(request(alice,"POST","/api/messages",Map.of("roomId",room,"content","original keyword")));String path="/api/messages/"+room+"/"+message.get("id").asText();
        assertEquals(403,request(bob,"PATCH",path,Map.of("content","forged","version",0)).statusCode());
        var changed=request(alice,"PATCH",path,Map.of("content","edited keyword","version",0));assertEquals(200,changed.statusCode(),changed.body());assertEquals(1,json(changed).get("version").asInt());
        assertEquals(409,request(alice,"PATCH",path,Map.of("content","stale edit","version",0)).statusCode());
        assertEquals(1,json(request(bob,"GET","/api/messages/"+room+"/search?q=keyword",null)).size());
        var deleted=request(alice,"DELETE",path+"?version=1",null);assertEquals(200,deleted.statusCode(),deleted.body());assertEquals("",json(deleted).get("content").asText());assertFalse(json(deleted).get("deletedAt").isNull());assertEquals(0,json(request(bob,"GET","/api/messages/"+room+"/search?q=keyword",null)).size());
    }
    @Test void unreadCountsArePersistentAndMembershipScoped()throws Exception{
        String alice=user("unread-a"),bob=user("unread-b"),eve=user("unread-e");String room=json(request(alice,"POST","/api/rooms/dm",Map.of("peerId",bob))).get("id").asText();var message=json(request(alice,"POST","/api/messages",Map.of("roomId",room,"content","private message")));
        assertEquals(1,json(request(bob,"GET","/api/notifications/unread",null)).get(room).asInt());assertFalse(json(request(alice,"GET","/api/notifications/unread",null)).has(room));assertFalse(json(request(eve,"GET","/api/notifications/unread",null)).has(room));
        assertEquals(403,request(eve,"GET","/api/messages/"+room+"/search?q=private",null).statusCode());
        assertEquals(204,request(bob,"POST","/api/messages/read",Map.of("roomId",room,"messageIds",List.of(message.get("id").asText()))).statusCode());assertFalse(json(request(bob,"GET","/api/notifications/unread",null)).has(room));
    }
    @Test void workspaceAssetsAreLocallyAvailable()throws Exception{
        for(String path:List.of("/home","/friends","/servers","/css/workspace.css","/js/workspace.js","/webjars/stomp__stompjs/7.3.0/bundles/stomp.umd.min.js","/webjars/sockjs-client/1.6.1/dist/sockjs.min.js")){var response=request(null,"GET",path,null);assertEquals(200,response.statusCode(),path+" "+response.body());}
    }
}
