package com.individual.messenger.service;

import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.dto.auth.LoginRequest;
import com.individual.messenger.dto.auth.LoginResponse;
import com.individual.messenger.dto.auth.RegisterRequest;
import com.individual.messenger.dto.auth.RegisterResponse;
import com.individual.messenger.exception.DuplicateLoginIdException;
import com.individual.messenger.repository.UserRepository;
import com.individual.messenger.security.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final CryptoService crypto;
    private final JwtUtil jwt;
    private final PasswordEncoder passwordEncoder; // ✅ DI로 주입

    public AuthService(UserRepository userRepo, CryptoService crypto, JwtUtil jwt, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.crypto = crypto;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResponse register(RegisterRequest req) {
        final String loginId = req.id == null ? "" : req.id.strip();
        final String userName = req.userName == null ? "" : req.userName.strip();
        final String rawPw = req.password == null ? "" : req.password;
        if (loginId.isBlank() || userName.isBlank() || rawPw.isBlank()) {
            throw new IllegalArgumentException("아이디, 표시 이름, 비밀번호는 필수입니다.");
        }
        if (!loginId.matches("[A-Za-z0-9_-]{3,64}") || "AI_BOT".equalsIgnoreCase(loginId))
            throw new IllegalArgumentException("아이디는 영문·숫자·_·- 조합의 3~64자여야 합니다.");
        if (userName.length() > 80 || userName.codePoints().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT)) throw new IllegalArgumentException("표시 이름을 확인해 주세요.");
        if (rawPw.length() < 8 || rawPw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("비밀번호는 8자 이상, UTF-8 기준 72바이트 이하여야 합니다.");
        if (userRepo.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException();
        }
        User user = new User();
        user.loginId = loginId;
        user.userName = userName;
        user.passwordHash = passwordEncoder.encode(rawPw);
        user.createdAt = Instant.now();
        // New registrations no longer collect phone numbers; existing user documents are not changed.
        try {
            userRepo.save(user);
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateLoginIdException(duplicate);
        }
        return new RegisterResponse(user.loginId, user.userName);
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
                u.legacyPhoneNumber = null; // Remove plaintext only after successful encryption on this authenticated migration.
            }
            userRepo.save(u);
        }

        if (u.passwordHash == null || !passwordEncoder.matches(rawPw, u.passwordHash)) {
            throw new IllegalArgumentException("잘못된 자격 증명입니다.");
        }

        String token = jwt.createToken(u.loginId, Map.of("name", u.userName));
        return new LoginResponse(token, u.loginId, u.userName);
    }

    public LoginResponse refresh(String loginId) {
        User user = userRepo.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new LoginResponse(jwt.createToken(loginId, Map.of("name", user.userName)), loginId, user.userName);
    }
}
