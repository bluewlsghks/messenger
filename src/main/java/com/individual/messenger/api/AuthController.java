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

    /** ?뚯썝媛?? 201 Created / 409 Conflict(以묐났 ID) */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            RegisterResponse res = authService.register(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (IllegalArgumentException dup) {
            // AuthService?먯꽌 DuplicateKey ?ы븿?섏뿬 IllegalArgumentException?쇰줈 ?듭씪
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "DUPLICATE_ID", "message", dup.getMessage()));
        }
    }

    /** 濡쒓렇?? 200 OK / 401 Unauthorized(?먭꺽 利앸챸 ?ㅻ쪟) */
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
     * ?≪꽭???좏겙 ?щ컻湲?
     * - JwtAuthFilter媛 Authentication ?ㅼ젙?대몢?덈떎???꾩젣
     * - 200 OK
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(Authentication auth) {
        String loginId = auth.getName();
        var user = authService.getByLoginId(loginId)  // 燧낉툘 ?쒕퉬?ㅻ줈 ?꾩엫(?꾨옒 異붽? 硫붿꽌??李멸퀬)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String token = authService.newAccessToken(loginId, user.userName);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "id", loginId,
                "userName", user.userName
        ));
    }
}


