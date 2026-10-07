package com.individual.messenger.controller;

import com.individual.messenger.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping("/unread")
    public Map<String, Long> unread(Principal principal) { return notifications.unread(principal); }
}
