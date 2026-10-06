package com.individual.messenger.service;

import com.individual.messenger.crypto.CryptoService;
import com.individual.messenger.domain.User;
import com.individual.messenger.dto.UserDtos.Profile;
import com.individual.messenger.repository.UserProfileRepository;
import com.individual.messenger.security.ChatAccessService;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class UserService {
    private final ChatAccessService access;
    private final CryptoService crypto;
    private final UserProfileRepository profiles;
    public UserService(ChatAccessService access, CryptoService crypto, UserProfileRepository profiles) {
        this.access = access; this.crypto = crypto; this.profiles = profiles;
    }
    public Profile me(Principal principal) {
        User user = access.actor(principal);
        String phone = user.phoneEnc == null || user.phoneEnc.isBlank() ? null : crypto.decryptString(user.phoneEnc);
        return new Profile(user.loginId, user.userName == null ? user.loginId : user.userName, phone, user.createdAt);
    }
    public Profile rename(Principal principal, String requestedName) {
        User user = access.actor(principal);
        if (requestedName == null) throw new IllegalArgumentException("표시 이름을 확인해 주세요.");
        String name = requestedName.strip();
        if (name.isBlank() || name.length() > 40 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("표시 이름을 확인해 주세요.");
        profiles.rename(user.mongoId, name);
        return new Profile(user.loginId, name, null, user.createdAt);
    }
}
