package com.individual.messenger.service;

import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.dto.auth.RegisterRequest;
import com.individual.messenger.exception.DuplicateLoginIdException;
import com.individual.messenger.repository.UserRepository;
import com.individual.messenger.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRegistrationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CryptoService crypto = mock(CryptoService.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(users, crypto, jwt, passwords);
    private RegisterRequest request(String id, String name, String password) {
        RegisterRequest request = new RegisterRequest();
        request.id = id; request.userName = name; request.password = password;
        return request;
    }
    @Test void signupHasNoPhoneAndPersistsExactlyOnce() {
        when(passwords.encode("Password!234")).thenReturn("encoded-password");
        var response = service.register(request(" new-user ", " 표시 이름 ", "Password!234"));
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users, times(1)).save(saved.capture());
        assertEquals("new-user", saved.getValue().loginId);
        assertEquals("표시 이름", saved.getValue().userName);
        assertEquals("encoded-password", saved.getValue().passwordHash);
        assertNull(saved.getValue().phoneEnc);
        assertNull(saved.getValue().legacyPhoneNumber);
        assertEquals("new-user", response.id);
        verify(users).existsByLoginId("new-user");
        verifyNoInteractions(crypto);
    }
    @Test void duplicateNormalizedIdIsAConflictWithoutSaving() {
        when(users.existsByLoginId("used")).thenReturn(true);
        assertThrows(DuplicateLoginIdException.class, () -> service.register(request(" used ", "Name", "Password!234")));
        verify(users, never()).save(any());
        verifyNoInteractions(crypto, passwords);
    }
    @Test void identityFieldsRemainRequired() {
        for (RegisterRequest request : new RegisterRequest[]{request(" ","Name","pw"),request("id"," ","pw"),request("id","Name"," ")})
            assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verifyNoInteractions(users, crypto, passwords);
    }
    @Test void duplicateRaceDoesNotRetryTheSave() {
        when(users.save(any(User.class))).thenThrow(new DuplicateKeyException("loginId"));
        assertThrows(DuplicateLoginIdException.class, () -> service.register(request("new", "Name", "Password!234")));
        verify(users, times(1)).save(any(User.class));
    }
}
