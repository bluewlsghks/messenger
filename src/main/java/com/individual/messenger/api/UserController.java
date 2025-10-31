package com.individual.messenger.api;

import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.repo.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepo;
    private final CryptoService crypto;

    public UserController(UserRepository userRepo, CryptoService crypto) {
        this.userRepo = userRepo; this.crypto = crypto;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        // JwtAuthFilter가 principal(username=loginId)을 세팅해줌
        String loginId = auth.getName();
        User u = userRepo.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String phone = (u.phoneEnc != null && !u.phoneEnc.isBlank())
                ? crypto.decryptString(u.phoneEnc) : null;

        return ResponseEntity.ok(Map.of(
                "id", u.loginId,
                "userName", u.userName,
                "phoneNumber", phone,
                "createdAt", u.createdAt != null ? u.createdAt : Instant.EPOCH
        ));
    }
}
