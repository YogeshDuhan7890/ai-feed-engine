package com.yogesh.controller;

import com.yogesh.model.CloseFriend;
import com.yogesh.model.User;
import com.yogesh.repository.CloseFriendRepository;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/close-friends")
@RequiredArgsConstructor
public class CloseFriendController {

    private final CloseFriendRepository closeFriendRepository;
    private final UserRepository userRepository;

    /** GET /api/close-friends — list */
    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        User me = me(auth);
        List<CloseFriend> rows = closeFriendRepository.findByUserIdOrderByCreatedAtDesc(me.getId());
        Set<Long> ids = rows.stream().map(CloseFriend::getFriendUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return rows.stream().map(r -> {
            User u = userMap.get(r.getFriendUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("friendUserId", r.getFriendUserId());
            m.put("name", u != null ? u.getName() : "User");
            m.put("username", u != null ? u.getUsername() : null);
            m.put("avatar", u != null ? u.getAvatar() : null);
            m.put("addedAt", r.getCreatedAt());
            return m;
        }).toList();
    }

    /** POST /api/close-friends/{friendUserId} — add */
    @PostMapping("/{friendUserId}")
    public Map<String, Object> add(@PathVariable Long friendUserId, Authentication auth) {
        User me = me(auth);
        if (friendUserId == null || friendUserId <= 0) {
            return Map.of("success", false, "message", "friendUserId required");
        }
        if (me.getId().equals(friendUserId)) {
            return Map.of("success", false, "message", "Khud ko close friend nahi bana sakte");
        }
        if (closeFriendRepository.existsByUserIdAndFriendUserId(me.getId(), friendUserId)) {
            return Map.of("success", true, "added", false, "message", "Already close friend");
        }
        CloseFriend cf = new CloseFriend();
        cf.setUserId(me.getId());
        cf.setFriendUserId(friendUserId);
        closeFriendRepository.save(cf);
        return Map.of("success", true, "added", true);
    }

    /** DELETE /api/close-friends/{friendUserId} — remove */
    @DeleteMapping("/{friendUserId}")
    public Map<String, Object> remove(@PathVariable Long friendUserId, Authentication auth) {
        User me = me(auth);
        closeFriendRepository.deleteByUserIdAndFriendUserId(me.getId(), friendUserId);
        return Map.of("success", true, "removed", true);
    }

    private User me(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}

