package com.individual.messenger.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping("/login")    public String login()  { return "login"; }
    @GetMapping("/register") public String register(){ return "register"; }
    @GetMapping("/rooms")    public String rooms()  { return "rooms"; }   // ✅
    @GetMapping("/chat/{roomId}") public String chat() { return "chat"; }   // templates/chat.html
    @GetMapping({"/", "/home"})         public String home()   { return "home"; }   // 선택: 루트도 rooms로
    @GetMapping("/friends")
    public String friends() {
        return "friends"; // src/main/resources/templates/friends.html
    }
    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied"; // 403 안내 페이지
    }
}

