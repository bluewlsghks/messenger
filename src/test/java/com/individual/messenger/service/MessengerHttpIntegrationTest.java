package com.individual.messenger.service;

import com.individual.messenger.domain.ChatServer;
import com.individual.messenger.domain.Message;
import com.individual.messenger.domain.User;
import com.individual.messenger.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.openai.enabled=false",
        "app.crypto.aesKeyBase64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secretBase64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessengerHttpIntegrationTest {
    static final String DATABASE = "messenger_http_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }
    @Autowired TestRestTemplate http;
    @Autowired MongoTemplate mongo;
    @Autowired JwtUtil jwt;
    @AfterAll void cleanup() { mongo.getDb().drop(); }
    HttpHeaders headers(String loginId) {
        User user = new User(); user.loginId = loginId; user.userName = "표시-" + loginId; mongo.save(user);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(jwt.createToken(loginId, Map.of()));
        headers.setContentType(MediaType.APPLICATION_JSON); return headers;
    }
    @Test void htmlIsPublicButApisAndRefreshRequireAuthentication() {
        assertEquals(HttpStatus.OK, http.getForEntity("/servers", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.getForEntity("/api/servers", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity("/api/auth/refresh", null, String.class).getStatusCode());
    }
    @Test void actualHttpFlowEnforcesChannelMembershipAndIdentity() {
        HttpHeaders alice = headers("http-alice"); HttpHeaders bob = headers("http-bob");
        var created = http.postForEntity("/api/servers", new HttpEntity<>(Map.of("name", "HTTP 서버"), alice), ChatServer.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode()); ChatServer server = created.getBody(); assertNotNull(server);
        String channelId = server.channels.get(0).id;
        String url = "/api/messages/" + channelId;
        assertEquals(HttpStatus.FORBIDDEN, http.exchange(url, HttpMethod.GET, new HttpEntity<>(bob), String.class).getStatusCode());
        var sent = http.postForEntity("/api/messages", new HttpEntity<>(Map.of("roomId", channelId, "content", "hello", "senderId", "http-bob", "senderName", "forged"), alice), Message.class);
        assertEquals(HttpStatus.OK, sent.getStatusCode()); assertNotNull(sent.getBody());
        assertEquals("http-alice", sent.getBody().senderId); assertEquals("표시-http-alice", sent.getBody().senderName);
        assertEquals(HttpStatus.FORBIDDEN, http.postForEntity("/api/dm/" + channelId + "/send?me=http-alice&content=forged", new HttpEntity<>(null, bob), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange(url + "?limit=0", HttpMethod.GET, new HttpEntity<>(alice), String.class).getStatusCode());
    }
}
