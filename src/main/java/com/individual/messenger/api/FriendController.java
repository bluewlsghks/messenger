package com.individual.messenger.api;

import com.individual.messenger.dto.FriendDto;
import com.individual.messenger.security.ChatAccessService;
import com.individual.messenger.service.FriendService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendService friends;
    private final ChatAccessService access;
    public FriendController(FriendService friends, ChatAccessService access) {
        this.friends = friends;
        this.access = access;
    }
    @GetMapping
    public List<FriendDto> list(Principal principal) {
        // JwtAuthFilter uses a String principal, not a UserDetails.username property.
        return friends.list(access.actor(principal).loginId);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(Principal principal, @Valid @RequestBody FriendRequest request) {
        friends.addFriend(access.actor(principal).loginId, request.friendId());
    }
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal principal, @RequestParam String friendId) {
        friends.remove(access.actor(principal).loginId, friendId);
    }
    public record FriendRequest(@NotBlank @Size(max = 100) String friendId) {}
}
