package com.individual.messenger.controller;

import com.individual.messenger.dto.auth.LoginRequest;
import com.individual.messenger.dto.auth.LoginResponse;
import com.individual.messenger.dto.auth.RegisterRequest;
import com.individual.messenger.dto.auth.RegisterResponse;
import com.individual.messenger.exception.DuplicateLoginIdException;
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
        } catch (DuplicateLoginIdException dup) {
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
    public ResponseEntity<LoginResponse> refresh(Authentication auth) {
        return ResponseEntity.ok(authService.refresh(auth.getName()));
    }
}
