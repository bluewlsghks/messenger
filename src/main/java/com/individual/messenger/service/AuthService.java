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
    private final PasswordEncoder passwordEncoder; // ??DIë¡?ì£¼ì…
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder();


    public AuthService(UserRepository userRepo, CryptoService crypto, JwtUtil jwt, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.crypto = crypto;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        // ???…ë ¥ê°??•ê·œ??
        final String loginId = req.id == null ? "" : req.id.trim();
        final String userName = req.userName == null ? "" : req.userName.trim();
        final String rawPw = req.password == null ? "" : req.password;
        final String phone = req.phoneNumber == null ? "" : req.phoneNumber.trim();

        if (loginId.isEmpty() || userName.isEmpty() || rawPw.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("?„ìˆ˜ ?…ë ¥???„ë½?˜ì—ˆ?µë‹ˆ??");
        }

        // ???¬ì „ ì¤‘ë³µ ì²´í¬(ì°¸ê³ : ê²½ìŸ ì¡°ê±´?€ ?„ë˜ DuplicateKeyException?¼ë¡œ ??ë²???ë§‰ìŒ)
        if (userRepo.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("?´ë? ì¡´ì¬?˜ëŠ” ID ?…ë‹ˆ??");
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
            // ???¸ë±??ê¸°ë°˜ ê²½ìŸ ì¡°ê±´ ë°©ì?
            throw new IllegalArgumentException("?´ë? ì¡´ì¬?˜ëŠ” ID ?…ë‹ˆ??");
        }

        return new RegisterResponse(u.loginId, u.userName);
    }

    public LoginResponse login(LoginRequest req) {
        final String id = req.id == null ? "" : req.id.trim();
        final String rawPw = req.password == null ? "" : req.password;

        // ???ˆê±°??ë¬¸ì„œê¹Œì? ì°¾ê³  ?¶ë‹¤ë©?findByAnyId ? ì?, ?„ë‹ˆë©?findByLoginId ê¶Œì¥
        User u = userRepo.findByAnyId(id)
                .orElseThrow(() -> new IllegalArgumentException("?˜ëª»???ê²© ì¦ëª…?…ë‹ˆ??"));

        // ???ˆê±°????ìµœì´ˆ ë¡œê·¸??ë§ˆì´ê·¸ë ˆ?´ì…˜ (?ˆì „??ê°•í™”)
        if ((u.passwordHash == null || u.passwordHash.isBlank()) && u.legacyPassword != null) {
            if (!rawPw.equals(u.legacyPassword)) {
                throw new IllegalArgumentException("?˜ëª»???ê²© ì¦ëª…?…ë‹ˆ??");
            }
            u.passwordHash = passwordEncoder.encode(u.legacyPassword);
            u.legacyPassword = null;

            if (u.loginId == null || u.loginId.isBlank()) {
                u.loginId = u.legacyId != null ? u.legacyId : id;
            }
            if ((u.phoneEnc == null || u.phoneEnc.isBlank()) && u.legacyPhoneNumber != null) {
                u.phoneEnc = crypto.encryptString(u.legacyPhoneNumber);
                // ?„ìš” ???ë¬¸ ?œê±°:
                // u.setLegacyPhoneNumber(null);
            }
            userRepo.save(u);
        }

        if (u.passwordHash == null || !passwordEncoder.matches(rawPw, u.passwordHash)) {
            throw new IllegalArgumentException("?˜ëª»???ê²© ì¦ëª…?…ë‹ˆ??");
        }

        String token = jwt.createToken(u.loginId, Map.of("name", u.userName));
        return new LoginResponse(token, u.loginId, u.userName);
    }

    // ?„ìš” ???¸ë??ì„œ ?ˆí¬ ?‘ê·¼
    public UserRepository userRepo() { return userRepo; }

    public String newAccessToken(String loginId, String userName) {
        return jwt.createToken(loginId, Map.of("name", userName));
    }

    // AuthService ?ˆì— ì¶”ê?
    public java.util.Optional<com.individual.messenger.domain.User> getByLoginId(String loginId) {
        return userRepo.findByLoginId(loginId);
    }

}

