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

    /** 회원가입: 201 Created / 409 Conflict(중복 ID) */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            RegisterResponse res = authService.register(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (IllegalArgumentException dup) {
            // AuthService에서 DuplicateKey 포함하여 IllegalArgumentException으로 통일
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "DUPLICATE_ID", "message", dup.getMessage()));
        }
    }

    /** 로그인: 200 OK / 401 Unauthorized(자격 증명 오류) */
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
     * 액세스 토큰 재발급:
     * - JwtAuthFilter가 Authentication 설정해두었다는 전제
     * - 200 OK
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(Authentication auth) {
        String loginId = auth.getName();
        var user = authService.getByLoginId(loginId)  // ⬅️ 서비스로 위임(아래 추가 메서드 참고)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String token = authService.newAccessToken(loginId, user.userName);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "id", loginId,
                "userName", user.userName
        ));
    }
}
