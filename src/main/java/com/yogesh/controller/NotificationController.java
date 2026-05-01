package com.yogesh.controller;

import com.yogesh.dto.NotificationDTO;
import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.NotificationService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /* =====================
       GET ALL NOTIFICATIONS
       GET /api/notifications
    ===================== */

    @GetMapping
    public List<NotificationDTO> getAll(Authentication auth) {

        User user = getUser(auth);
        return notificationService.getForUser(user.getId());
    }

    /* =====================
       UNREAD COUNT
       GET /api/notifications/unread-count
    ===================== */

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {

        User user = getUser(auth);
        long count = notificationService.getUnreadCount(user.getId());
        return Map.of("count", count);
    }

    /* =====================
       MARK ONE READ
       POST /api/notifications/{id}/read
    ===================== */

    @PostMapping("/{id}/read")
    public Map<String, String> markRead(@PathVariable Long id, Authentication auth) {

        User user = getUser(auth);
        notificationService.markRead(id, user.getId());
        return Map.of("status", "ok");
    }

    /* =====================
       MARK ALL READ
       POST /api/notifications/read-all
    ===================== */

    @PostMapping("/read-all")
    public Map<String, String> markAllRead(Authentication auth) {

        User user = getUser(auth);
        notificationService.markAllRead(user.getId());
        return Map.of("status", "ok");
    }

    /* =====================
       HELPER
    ===================== */

    private User getUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return userRepository
                .findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}