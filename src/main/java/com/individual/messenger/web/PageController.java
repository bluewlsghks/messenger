package com.individual.messenger.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/register") public String register() { return "register"; }
    @GetMapping({"/", "/home", "/friends", "/rooms", "/chat/{roomId}"})
    public String workspace() { return "workspace"; }
    @GetMapping("/access-denied") public String accessDenied() { return "access-denied"; }
}
