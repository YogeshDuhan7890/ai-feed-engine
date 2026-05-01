package com.yogesh.controller;

import com.yogesh.dto.FollowUserDTO;
import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FollowService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @PostMapping("/follow/{userId}")
    public ResponseEntity<Map<String, Object>> follow(@PathVariable Long userId, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        var result = followService.follow(me.getId(), userId);
        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "status", result.status(),
                "message", result.message()));
    }

    @PostMapping("/unfollow/{userId}")
    public ResponseEntity<Map<String, Object>> unfollow(@PathVariable Long userId, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        var result = followService.unfollow(me.getId(), userId);
        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "status", result.status(),
                "message", result.message()));
    }

    @GetMapping("/follow-requests/pending")
    public ResponseEntity<Map<String, Object>> pendingRequests(Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<FollowUserDTO> requests = followService.getPendingRequests(me.getId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", requests.size(),
                "requests", requests));
    }

    @PostMapping("/follow-requests/{userId}/accept")
    public ResponseEntity<Map<String, Object>> acceptRequest(@PathVariable Long userId, Authentication authentication) {
        try {
            User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
            var result = followService.acceptRequest(me.getId(), userId);
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "status", result.status(),
                    "message", result.message()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", safeMessage(e, "follow.request.accept.error", "Follow request accept nahi ho paayi")));
        }
    }

    @PostMapping("/follow-requests/{userId}/reject")
    public ResponseEntity<Map<String, Object>> rejectRequest(@PathVariable Long userId, Authentication authentication) {
        try {
            User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
            var result = followService.rejectRequest(me.getId(), userId);
            return ResponseEntity.ok(Map.of(
                    "success", result.success(),
                    "status", result.status(),
                    "message", result.message()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", safeMessage(e, "follow.request.reject.error", "Follow request reject nahi ho paayi")));
        }
    }

    /**
     * GET /api/followers/{userId}
     * Us user ke saare followers
     */
    @GetMapping("/followers/{userId}")
    public List<FollowUserDTO> getFollowers(@PathVariable Long userId, Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        return followService.getFollowers(userId, currentUserId);
    }

    /**
     * GET /api/following/{userId}
     * Us user ne kise follow kiya
     */
    @GetMapping("/following/{userId}")
    public List<FollowUserDTO> getFollowing(@PathVariable Long userId, Authentication authentication) {
        Long currentUserId = getCurrentUserId(authentication);
        return followService.getFollowing(userId, currentUserId);
    }

    private Long getCurrentUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            return null;
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    private String safeMessage(Exception e, String fallbackCode, String fallbackMessage) {
        if (e != null && e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return messageSource.getMessage(fallbackCode, null, fallbackMessage, LocaleContextHolder.getLocale());
    }
}
