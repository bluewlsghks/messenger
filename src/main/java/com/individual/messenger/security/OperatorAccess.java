package com.individual.messenger.security;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Separate from chat JWTs: a signed-in member is not a system operator. */
@Component
public class OperatorAccess {
    private final byte[] secret;
    public OperatorAccess(@Value("${app.operations.token:}") String token) {
        if(!token.isBlank() && token.getBytes(StandardCharsets.UTF_8).length<32)
            throw new IllegalArgumentException("APP_OPERATIONS_TOKEN must contain at least 32 bytes");
        secret=token.isBlank()?null:token.getBytes(StandardCharsets.UTF_8);
    }
    public boolean allows(HttpServletRequest request) {
        String supplied=request.getHeader("X-Operations-Token");
        return secret!=null && supplied!=null && supplied.length()<=1024 &&
                MessageDigest.isEqual(secret,supplied.getBytes(StandardCharsets.UTF_8));
    }
}
