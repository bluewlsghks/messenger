package com.individual.messenger.controller;

import com.individual.messenger.security.JwtUtil;
import com.individual.messenger.service.WebPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class WebPushController {
    private final WebPushService push;
    private final JwtUtil jwt;
    public WebPushController(WebPushService push,JwtUtil jwt) { this.push=push; this.jwt=jwt; }
    public record Keys(@NotBlank @Size(max=128) String p256dh,@NotBlank @Size(max=32) String auth) {}
    public record Subscription(@NotBlank @Size(max=2048) String endpoint,@NotNull @Valid Keys keys) {}
    public record Unsubscribe(@NotBlank @Size(max=2048) String endpoint) {}
    public record Preferences(@NotBlank @Size(max=2048) String endpoint,
                              @Size(max=200) java.util.List<@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String> mutedRooms) {}
    @PutMapping("/preferences") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void preferences(@RequestHeader("Authorization") String token,@Valid @RequestBody Preferences request,Principal principal) {
        push.preferences(principal.getName(),jwt.sessionId(token.substring(7)),request.endpoint(),request.mutedRooms());
    }
    @GetMapping("/config") public Map<String,Object> configuration() { return push.configuration(); }
    @PostMapping("/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@RequestHeader("Authorization") String token,@Valid @RequestBody Subscription request,Principal principal) {
        push.subscribe(principal.getName(),jwt.sessionId(token.substring(7)),request.endpoint(),request.keys().p256dh(),request.keys().auth());
    }
    @DeleteMapping("/subscriptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody Unsubscribe request,Principal principal) { push.unsubscribe(principal.getName(),request.endpoint()); }
}
