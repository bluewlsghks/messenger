package com.individual.messenger.api;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dev")
public class DevHashController {
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder();
    @GetMapping("/hash")
    public String hash(@RequestParam String raw) {
        return bCrypt.encode(raw);
    }
}
