package com.individual.messenger.api;

import com.individual.messenger.api.dto.RegisterDtos.RegisterRequest;
import com.individual.messenger.api.dto.RegisterDtos.RegisterResponse;
import com.individual.messenger.api.dto.LoginDtos.LoginRequest;
import com.individual.messenger.api.dto.LoginDtos.LoginResponse;
import com.individual.messenger.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    /** ?åÏõêÍ∞Ä?? 201 Created / 409 Conflict(Ï§ëÎ≥µ ID) */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            RegisterResponse res = authService.register(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (IllegalArgumentException dup) {
            // AuthService?êÏÑú DuplicateKey ?¨Ìï®?òÏó¨ IllegalArgumentException?ºÎ°ú ?µÏùº
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "DUPLICATE_ID", "message", dup.getMessage()));
        }
    }

    /** Î°úÍ∑∏?? 200 OK / 401 Unauthorized(?êÍ≤© Ï¶ùÎ™Ö ?§Î•ò) */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            LoginResponse res = authService.login(req);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException bad) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_CREDENTIALS", "message", bad.getMessage()));
        }
    }

    /**
     * ?°ÏÑ∏???†ÌÅ∞ ?¨Î∞úÍ∏?
     * - JwtAuthFilterÍ∞Ä Authentication ?§Ï†ï?¥Îëê?àÎã§???ÑÏ†ú
     * - 200 OK
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(Authentication auth) {
        String loginId = auth.getName();
        var user = authService.getByLoginId(loginId)  // ‚¨ÖÔ∏è ?úÎπÑ?§Î°ú ?ÑÏûÑ(?ÑÎûò Ï∂îÍ? Î©îÏÑú??Ï∞∏Í≥†)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String token = authService.newAccessToken(loginId, user.userName);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "id", loginId,
                "userName", user.userName
        ));
    }
}
