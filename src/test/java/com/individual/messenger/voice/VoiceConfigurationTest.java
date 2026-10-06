package com.individual.messenger.voice;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoiceConfigurationTest {
    @Test void noTurnByDefaultAndHostOnlyConfigurationIsPossible() {
        var config = new VoiceConfiguration("stun:stun.example.test:3478", "", "", false).forUser("alice");
        assertEquals("all", config.get("iceTransportPolicy"));
        assertEquals(List.of(Map.of("urls", List.of("stun:stun.example.test:3478"))), config.get("iceServers"));
        assertEquals(List.of(), new VoiceConfiguration("", "", "", false).forUser("alice").get("iceServers"));
    }
    @Test void failsClosedForInvalidRelayConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceConfiguration("", "turn:example.test", "", false));
        assertThrows(IllegalArgumentException.class, () -> new VoiceConfiguration("", "", "", true));
        assertThrows(IllegalArgumentException.class, () -> new VoiceConfiguration("https://example.test", "", "", false));
    }
    @Test void issuesExpiringHmacCredentialsWithoutExposingSharedSecret() throws Exception {
        String secret = "test-only-shared-secret";
        var config = new VoiceConfiguration("stun:example.test", "turn:example.test:3478,turns:example.test:5349", secret, true).forUser("alice");
        assertEquals("relay", config.get("iceTransportPolicy"));
        var servers = (List<?>) config.get("iceServers"); assertEquals(1, servers.size());
        var turn = (Map<?, ?>) servers.getFirst();
        String username = (String) turn.get("username");
        long expiry = Long.parseLong(username.split(":")[0]);
        assertTrue(expiry >= Instant.now().getEpochSecond() + 7190);
        assertTrue(expiry <= Instant.now().getEpochSecond() + 7200);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        assertEquals(Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8))), turn.get("credential"));
        assertFalse(config.toString().contains(secret));
    }
}
