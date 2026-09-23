package com.individual.messenger.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ServerPageController {
    @GetMapping("/servers") public String servers() { return "workspace"; }
}
