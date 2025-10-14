package com.individual.messenger.api;

import com.individual.messenger.api.dto.RegisterDtos.RegisterRequest;
import com.individual.messenger.api.dto.RegisterDtos.RegisterResponse;
import com.individual.messenger.api.dto.LoginDtos.LoginRequest;
import com.individual.messenger.api.dto.LoginDtos.LoginResponse;
import com.individual.messenger.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            RegisterResponse res = authService.register(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (IllegalArgumentException dup) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("error", "DUPLICATE_ID", "message", dup.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            LoginResponse res = authService.login(req);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException bad) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("error", "INVALID_CREDENTIALS", "message", bad.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(org.springframework.security.core.Authentication auth) {
        String loginId = auth.getName(); // JwtAuthFilter가 인증해둠
        // name 클레임(표시용)도 다시 담아주기
        var user = this.authService   // 필요하면 서비스에 getByLoginId 추가. 간단히 레포 직접 써도 무방
                .userRepo().findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String token = this.authService.newAccessToken(loginId, user.userName);
        return ResponseEntity.ok(java.util.Map.of(
                "token", token,
                "id", loginId,
                "userName", user.userName
        ));
    }
}
