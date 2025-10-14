package com.individual.messenger.service;

import com.individual.messenger.api.dto.RegisterDtos.RegisterRequest;
import com.individual.messenger.api.dto.RegisterDtos.RegisterResponse;
import com.individual.messenger.api.dto.LoginDtos.LoginRequest;
import com.individual.messenger.api.dto.LoginDtos.LoginResponse;
import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.repo.UserRepository;
import com.individual.messenger.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuthService {
    private final UserRepository userRepo;
    private final CryptoService crypto;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepo, CryptoService crypto, JwtUtil jwt) {
        this.userRepo = userRepo; this.crypto = crypto; this.jwt = jwt;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (userRepo.existsByLoginId(req.id)) throw new IllegalArgumentException("ID already exists");
        User u = new User();
        u.loginId = req.id;
        u.userName = req.userName;
        u.passwordHash = bCrypt.encode(req.password);
        u.phoneEnc = crypto.encryptString(req.phoneNumber);
        userRepo.save(u);
        return new RegisterResponse(u.loginId, u.userName);
    }

    public LoginResponse login(LoginRequest req) {
        // 레거시 문서도 찾도록
        User u = userRepo.findByAnyId(req.id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // 레거시 → 최초 로그인 시 자동 마이그레이션
        if ((u.passwordHash == null || u.passwordHash.isBlank()) && u.legacyPassword != null) {
            if (!req.password.equals(u.legacyPassword)) throw new IllegalArgumentException("Invalid credentials");
            u.passwordHash = bCrypt.encode(u.legacyPassword);
            u.legacyPassword = null;
            if (u.loginId == null || u.loginId.isBlank()) u.loginId = (u.legacyId != null) ? u.legacyId : req.id;
            if ((u.phoneEnc == null || u.phoneEnc.isBlank()) && u.legacyPhoneNumber != null) {
                u.phoneEnc = crypto.encryptString(u.legacyPhoneNumber);
                // u.legacyPhoneNumber = null; // 원문 제거하려면 주석 해제
            }
            userRepo.save(u);
        }

        if (!bCrypt.matches(req.password, u.passwordHash)) throw new IllegalArgumentException("Invalid credentials");
        String token = jwt.createToken(u.loginId, Map.of("name", u.userName));
        return new LoginResponse(token, u.loginId, u.userName);
    }

    // 레포 접근이 필요하면 간단히 패키지 내 getter 제공 (또는 별도 메서드로 조회)
    public com.individual.messenger.repo.UserRepository userRepo() { return userRepo; }

    public String newAccessToken(String loginId, String userName) {
        return jwt.createToken(loginId, java.util.Map.of("name", userName));
    }
}
