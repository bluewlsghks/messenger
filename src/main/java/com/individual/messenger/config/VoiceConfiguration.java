package com.individual.messenger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableScheduling
public class VoiceConfiguration {
    private final List<String> stunUrls, turnUrls;
    private final String turnSecret;
    private final boolean relayOnly;
    public VoiceConfiguration(
            @Value("${VOICE_STUN_URLS:stun:stun.l.google.com:19302}") String stunUrls,
            @Value("${VOICE_TURN_URLS:}") String turnUrls,
            @Value("${VOICE_TURN_SECRET:}") String turnSecret,
            @Value("${VOICE_RELAY_ONLY:false}") boolean relayOnly) {
        this.stunUrls = urls(stunUrls, "stun:", "stuns:");
        this.turnUrls = urls(turnUrls, "turn:", "turns:");
        this.turnSecret = turnSecret; this.relayOnly = relayOnly;
        if (!this.turnUrls.isEmpty() && turnSecret.isBlank())
            throw new IllegalArgumentException("VOICE_TURN_SECRET is required when TURN is configured");
        if (relayOnly && this.turnUrls.isEmpty())
            throw new IllegalArgumentException("VOICE_RELAY_ONLY requires a TURN server");
    }
    public Map<String, Object> forUser(String loginId) {
        List<Map<String, Object>> servers = new ArrayList<>();
        if (!stunUrls.isEmpty() && !relayOnly) servers.add(Map.of("urls", stunUrls));
        if (!turnUrls.isEmpty()) {
            // coturn REST credentials expire after 2 hours; calls are limited to 1 hour.
            String username = (Instant.now().getEpochSecond() + 7200) + ":" + loginId;
            servers.add(Map.of("urls", turnUrls, "username", username, "credential", credential(username)));
        }
        return Map.of("iceServers", servers, "iceTransportPolicy", relayOnly ? "relay" : "all");
    }
    private String credential(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(turnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("TURN credential generation failed", error); }
    }
    private static List<String> urls(String value, String first, String second) {
        List<String> urls = Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (urls.stream().anyMatch(url -> !(url.startsWith(first) || url.startsWith(second))))
            throw new IllegalArgumentException("Invalid STUN/TURN URL scheme");
        return urls;
    }
}
