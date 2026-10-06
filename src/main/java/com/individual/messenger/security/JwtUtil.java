package com.individual.messenger.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.individual.messenger.service.SessionService sessions;
    private final SecretKey key;
    private final long expiresMillis;

    public JwtUtil(@Value("${app.jwt.secretBase64}") String base64Key,
                   @Value("${app.jwt.expiresMinutes}") long expiresMinutes) {
        byte[] k = Decoders.BASE64.decode(base64Key);
        this.key = Keys.hmacShaKeyFor(k);
        this.expiresMillis = expiresMinutes * 60_000L;
    }

    public String createToken(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private Claims claims(String token) {
        if(token==null || token.length()>8192)throw new IllegalArgumentException("Invalid JWT size");
        Claims claims = Jwts.parser().verifyWith(key).zip().clear().and()
                .sig().clear().add(Jwts.SIG.HS256).and().build().parseSignedClaims(token).getPayload();
        String sid = claims.get("sid", String.class);
        if (sid != null && sessions != null && !sessions.active(sid, claims.getSubject()))
            throw new IllegalArgumentException("Revoked session");
        return claims;
    }
    public String getSubject(String token) { return claims(token).getSubject(); }
    public String extractUsername(String token) { return getSubject(token); }
    public String sessionId(String token) { return claims(token).get("sid", String.class); }
    public boolean validate(String token) {
        try { claims(token); return true; }
        catch (JwtException | IllegalArgumentException invalid) { return false; }
    }
}
