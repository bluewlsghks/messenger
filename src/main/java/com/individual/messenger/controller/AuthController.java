package com.individual.messenger.controller;

import com.individual.messenger.dto.auth.*;
import com.individual.messenger.domain.AuthSession;
import com.individual.messenger.exception.DuplicateLoginIdException;
import com.individual.messenger.service.AuthService;
import com.individual.messenger.service.SessionService;
import com.individual.messenger.security.JwtUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;
    private final JwtUtil jwt;
    private final boolean secureCookie;
    private final Set<String> origins;
    public AuthController(AuthService auth, SessionService sessions, JwtUtil jwt,
                          @Value("${app.auth.secure-cookie:false}") boolean secureCookie,
                          @Value("${app.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}") String[] origins) {
        this.auth = auth; this.sessions = sessions; this.jwt = jwt; this.secureCookie = secureCookie || java.util.Arrays.stream(origins).anyMatch(origin -> origin.startsWith("https://"));
        this.origins = Set.copyOf(Arrays.asList(origins));
    }
    @PostMapping("/register") public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try { return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(req)); }
        catch (DuplicateLoginIdException duplicate) {
            return ResponseEntity.status(409).body(Map.of("error", "DUPLICATE_ID", "message", duplicate.getMessage()));
        }
    }
    @PostMapping("/login") public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            LoginResponse user = auth.login(req);
            return issue(sessions.open(user.id), user.userName);
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.status(401).body(Map.of("error", "INVALID_CREDENTIALS", "message", "잘못된 자격 증명입니다."));
        }
    }
    @PostMapping("/refresh") public ResponseEntity<LoginResponse> refresh(HttpServletRequest request,
            @CookieValue(name = "messenger_refresh", required = false) String token) {
        sameOriginCommand(request);
        var issued = sessions.rotate(token);
        return issue(issued, auth.refresh(issued.userId()).userName);
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request,
            @CookieValue(name = "messenger_refresh", required = false) String token) {
        sameOriginCommand(request); sessions.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }
    @GetMapping("/sessions") public List<AuthSession> list(Principal principal) { return sessions.list(principal.getName()); }
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Principal principal, @PathVariable String id) { sessions.revokeOwned(id, principal.getName()); }
    private ResponseEntity<LoginResponse> issue(SessionService.Issued session, String name) {
        String access = jwt.createToken(session.userId(), Map.of("name", name == null ? session.userId() : name, "sid", session.sessionId()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), Duration.between(Instant.now(), session.expiresAt())).toString())
                .body(new LoginResponse(access, session.userId(), name));
    }
    private ResponseCookie cookie(String token, Duration age) {
        return ResponseCookie.from("messenger_refresh", token).httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path("/api/auth").maxAge(age).build();
    }
    private void sameOriginCommand(HttpServletRequest request) {
        // Cookie endpoints require a non-simple header plus an exact Origin allowlist.
        String origin = request.getHeader("Origin");
        if (!"XMLHttpRequest".equals(request.getHeader("X-Requested-With")) || (origin != null && !origins.contains(origin)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동일 출처의 요청만 허용됩니다.");
    }
}
