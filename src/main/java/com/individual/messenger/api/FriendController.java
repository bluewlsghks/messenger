package com.individual.messenger.api;

import com.individual.messenger.dto.AddFriendRequest;
import com.individual.messenger.dto.FriendDto;
import com.individual.messenger.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping
    public ResponseEntity<Void> addFriend(@AuthenticationPrincipal(expression = "username") String me,
                                          @RequestBody AddFriendRequest req) {
        friendService.addFriend(me, req.getFriendId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<FriendDto> list(@AuthenticationPrincipal(expression = "username") String me) {
        return friendService.list(me);
    }
}
