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
    private final PasswordEncoder passwordEncoder; // ??DI濡?二쇱엯
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder();


    public AuthService(UserRepository userRepo, CryptoService crypto, JwtUtil jwt, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.crypto = crypto;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        // ???낅젰媛??뺢퇋??
        final String loginId = req.id == null ? "" : req.id.trim();
        final String userName = req.userName == null ? "" : req.userName.trim();
        final String rawPw = req.password == null ? "" : req.password;
        final String phone = req.phoneNumber == null ? "" : req.phoneNumber.trim();

        if (loginId.isEmpty() || userName.isEmpty() || rawPw.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("?꾩닔 ?낅젰???꾨씫?섏뿀?듬땲??");
        }

        // ???ъ쟾 以묐났 泥댄겕(李멸퀬: 寃쎌웳 議곌굔? ?꾨옒 DuplicateKeyException?쇰줈 ??踰???留됱쓬)
        if (userRepo.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("?대? 議댁옱?섎뒗 ID ?낅땲??");
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
            // ???몃뜳??湲곕컲 寃쎌웳 議곌굔 諛⑹?
            throw new IllegalArgumentException("?대? 議댁옱?섎뒗 ID ?낅땲??");
        }

        return new RegisterResponse(u.loginId, u.userName);
    }

    public LoginResponse login(LoginRequest req) {
        final String id = req.id == null ? "" : req.id.trim();
        final String rawPw = req.password == null ? "" : req.password;

        // ???덇굅??臾몄꽌源뚯? 李얘퀬 ?띕떎硫?findByAnyId ?좎?, ?꾨땲硫?findByLoginId 沅뚯옣
        User u = userRepo.findByAnyId(id)
                .orElseThrow(() -> new IllegalArgumentException("?섎せ???먭꺽 利앸챸?낅땲??"));

        // ???덇굅????理쒖큹 濡쒓렇??留덉씠洹몃젅?댁뀡 (?덉쟾??媛뺥솕)
        if ((u.passwordHash == null || u.passwordHash.isBlank()) && u.legacyPassword != null) {
            if (!rawPw.equals(u.legacyPassword)) {
                throw new IllegalArgumentException("?섎せ???먭꺽 利앸챸?낅땲??");
            }
            u.passwordHash = passwordEncoder.encode(u.legacyPassword);
            u.legacyPassword = null;

            if (u.loginId == null || u.loginId.isBlank()) {
                u.loginId = u.legacyId != null ? u.legacyId : id;
            }
            if ((u.phoneEnc == null || u.phoneEnc.isBlank()) && u.legacyPhoneNumber != null) {
                u.phoneEnc = crypto.encryptString(u.legacyPhoneNumber);
                // ?꾩슂 ???먮Ц ?쒓굅:
                // u.setLegacyPhoneNumber(null);
            }
            userRepo.save(u);
        }

        if (u.passwordHash == null || !passwordEncoder.matches(rawPw, u.passwordHash)) {
            throw new IllegalArgumentException("?섎せ???먭꺽 利앸챸?낅땲??");
        }

        String token = jwt.createToken(u.loginId, Map.of("name", u.userName));
        return new LoginResponse(token, u.loginId, u.userName);
    }

    // ?꾩슂 ???몃??먯꽌 ?덊룷 ?묎렐
    public UserRepository userRepo() { return userRepo; }

    public String newAccessToken(String loginId, String userName) {
        return jwt.createToken(loginId, Map.of("name", userName));
    }

    // AuthService ?덉뿉 異붽?
    public java.util.Optional<com.individual.messenger.domain.User> getByLoginId(String loginId) {
        return userRepo.findByLoginId(loginId);
    }

}


