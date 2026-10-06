package com.individual.messenger.controller;

import com.individual.messenger.dto.UserDtos.*;
import com.individual.messenger.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }
    @GetMapping("/me")
    public Profile me(Principal principal) { return users.me(principal); }
    @PatchMapping("/me")
    public Profile rename(Principal principal, @Valid @RequestBody Rename request) {
        return users.rename(principal, request.userName());
    }
}
