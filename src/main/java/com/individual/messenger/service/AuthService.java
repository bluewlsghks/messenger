package com.individual.messenger.service;

import com.individual.messenger.api.dto.RegisterDtos.RegisterRequest;
import com.individual.messenger.api.dto.RegisterDtos.RegisterResponse;
import com.individual.messenger.api.dto.LoginDtos.LoginRequest;
import com.individual.messenger.api.dto.LoginDtos.LoginResponse;
import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.repo.UserRepository;
import com.individual.messenger.security.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final CryptoService crypto;
    private final JwtUtil jwt;
    private final PasswordEncoder passwordEncoder; // ✅ DI로 주입
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder();


    public AuthService(UserRepository userRepo, CryptoService crypto, JwtUtil jwt, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.crypto = crypto;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        // ✅ 입력값 정규화
        final String loginId = req.id == null ? "" : req.id.trim();
        final String userName = req.userName == null ? "" : req.userName.trim();
        final String rawPw = req.password == null ? "" : req.password;
        final String phone = req.phoneNumber == null ? "" : req.phoneNumber.trim();

        if (loginId.isEmpty() || userName.isEmpty() || rawPw.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("필수 입력이 누락되었습니다.");
        }

        // ✅ 사전 중복 체크(참고: 경쟁 조건은 아래 DuplicateKeyException으로 한 번 더 막음)
        if (userRepo.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 존재하는 ID 입니다.");
        }

        User u = new User();
        u.loginId = req.id;
        u.userName = req.userName;
        u.passwordHash = bCrypt.encode(req.password);
        u.phoneEnc = crypto.encryptString(req.phoneNumber);
        u.createdAt = Instant.now();
        userRepo.save(u);

        try {
            userRepo.save(u);
        } catch (DuplicateKeyException e) {
            // ✅ 인덱스 기반 경쟁 조건 방지
            throw new IllegalArgumentException("이미 존재하는 ID 입니다.");
        }

        return new RegisterResponse(u.loginId, u.userName);
    }

    public LoginResponse login(LoginRequest req) {
        final String id = req.id == null ? "" : req.id.trim();
        final String rawPw = req.password == null ? "" : req.password;

        // ✅ 레거시 문서까지 찾고 싶다면 findByAnyId 유지, 아니면 findByLoginId 권장
        User u = userRepo.findByAnyId(id)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 자격 증명입니다."));

        // ✅ 레거시 → 최초 로그인 마이그레이션 (안전성 강화)
        if ((u.passwordHash == null || u.passwordHash.isBlank()) && u.legacyPassword != null) {
            if (!rawPw.equals(u.legacyPassword)) {
                throw new IllegalArgumentException("잘못된 자격 증명입니다.");
            }
            u.passwordHash = passwordEncoder.encode(u.legacyPassword);
            u.legacyPassword = null;

            if (u.loginId == null || u.loginId.isBlank()) {
                u.loginId = u.legacyId != null ? u.legacyId : id;
            }
            if ((u.phoneEnc == null || u.phoneEnc.isBlank()) && u.legacyPhoneNumber != null) {
                u.phoneEnc = crypto.encryptString(u.legacyPhoneNumber);
                // 필요 시 원문 제거:
                // u.setLegacyPhoneNumber(null);
            }
            userRepo.save(u);
        }

        if (u.passwordHash == null || !passwordEncoder.matches(rawPw, u.passwordHash)) {
            throw new IllegalArgumentException("잘못된 자격 증명입니다.");
        }

        String token = jwt.createToken(u.loginId, Map.of("name", u.userName));
        return new LoginResponse(token, u.loginId, u.userName);
    }

    // 필요 시 외부에서 레포 접근
    public UserRepository userRepo() { return userRepo; }

    public String newAccessToken(String loginId, String userName) {
        return jwt.createToken(loginId, Map.of("name", userName));
    }

    // AuthService 안에 추가
    public java.util.Optional<com.individual.messenger.domain.User> getByLoginId(String loginId) {
        return userRepo.findByLoginId(loginId);
    }

}
