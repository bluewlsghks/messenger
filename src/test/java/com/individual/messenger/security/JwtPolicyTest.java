package com.individual.messenger.security;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class JwtPolicyTest {
    private final byte[] secret=new byte[64];
    private JwtUtil jwt(){return new JwtUtil(Base64.getEncoder().encodeToString(secret),15);}
    @Test void validHs256TokensStillRoundTrip() {
        String value=jwt().createToken("alice",Map.of());assertTrue(jwt().validate(value));assertEquals("alice",jwt().getSubject(value));
    }
    @Test void unsupportedAlgorithmAndCompressionAreRejected() {
        var key=Keys.hmacShaKeyFor(secret);
        String other=Jwts.builder().subject("alice").signWith(key,Jwts.SIG.HS384).compact();assertFalse(jwt().validate(other));
        String zipped=Jwts.builder().subject("alice").claim("padding","a".repeat(10000)).compressWith(Jwts.ZIP.DEF).signWith(key,Jwts.SIG.HS256).compact();assertFalse(jwt().validate(zipped));
    }
    @Test void expiredAndOversizedTokensAreRejected() {
        String expired=Jwts.builder().subject("alice").expiration(new Date(1)).signWith(Keys.hmacShaKeyFor(secret),Jwts.SIG.HS256).compact();
        assertFalse(jwt().validate(expired));assertFalse(jwt().validate("a".repeat(8193)));
    }
}
