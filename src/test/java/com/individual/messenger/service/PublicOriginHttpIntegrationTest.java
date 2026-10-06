package com.individual.messenger.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/** Simulate TLS ending at the tunnel, with plain HTTP from tunnel to the loopback server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.forward-headers-strategy=none",
        "app.allowed-origins=https://test-preview.trycloudflare.com",
        "app.openai.enabled=false",
        "app.crypto.aesKeyBase64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.secretBase64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicOriginHttpIntegrationTest {
    private static final String ORIGIN = "https://test-preview.trycloudflare.com";
    private static final String DATABASE = "messenger_public_test_" + UUID.randomUUID().toString().replace("-", "");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired MongoTemplate mongo;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
        properties.add("spring.mongodb.database", () -> DATABASE);
    }
    @AfterAll void cleanup() { mongo.getDb().drop(); }

    private HttpRequest.Builder get(String path, String origin) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("Origin", origin);
    }
    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void exactHttpsOriginWorksOverHttpForLoginAndSockJs() throws Exception {
        for (String path : new String[]{"/login", "/ws-stomp/info", "/js/realtime.js"}) {
            var response = send(get(path, ORIGIN));
            assertEquals(200, response.statusCode(), path);
            assertEquals(ORIGIN, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        }
    }
    @Test void authenticatedApiPreflightAllowsAuthorizationButDoesNotAuthenticate() throws Exception {
        var preflight = send(get("/api/friends", ORIGIN).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
        assertEquals(200, preflight.statusCode());
        assertEquals(401, send(get("/api/friends", ORIGIN)).statusCode());
    }
    @Test void otherTunnelOriginsAndForgedForwardedHeadersAreRejected() throws Exception {
        for (String origin : new String[]{"https://another-preview.trycloudflare.com", "https://attacker.example"}) {
            var response = send(get("/ws-stomp/info", origin)
                    .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "attacker.example"));
            assertEquals(403, response.statusCode());
        }
    }
    @Test void realWebSocketHandshakeAcceptsOnlyTheIssuedOrigin() {
        URI endpoint = URI.create("ws://127.0.0.1:" + port + "/ws-stomp/websocket");
        WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .header("Origin", ORIGIN).buildAsync(endpoint, new WebSocket.Listener() {}).join();
        socket.abort(); // Transport only; STOMP JWT authentication is tested by the existing suite.
        CompletionException error = assertThrows(CompletionException.class, () -> client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5)).header("Origin", "https://another-preview.trycloudflare.com")
                .buildAsync(endpoint, new WebSocket.Listener() {}).join());
        assertInstanceOf(WebSocketHandshakeException.class, error.getCause());
        assertEquals(403, ((WebSocketHandshakeException) error.getCause()).getResponse().statusCode());
    }
}
