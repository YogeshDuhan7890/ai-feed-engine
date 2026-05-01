package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.BlockService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/block")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;
    private final UserRepository userRepository;

    /** POST /api/block/{userId} — block karo */
    @PostMapping("/{userId}")
    public Map<String, Object> block(@PathVariable Long userId, Authentication auth) {
        User me = getUser(auth);
        return blockService.block(me.getId(), userId);
    }

    /** POST /api/unblock/{userId} — unblock karo */
    @PostMapping("/unblock/{userId}")
    public Map<String, Object> unblock(@PathVariable Long userId, Authentication auth) {
        User me = getUser(auth);
        return blockService.unblock(me.getId(), userId);
    }

    /** GET /api/block/list — meri block list */
    @GetMapping("/list")
    public List<Map<String, Object>> list(Authentication auth) {
        User me = getUser(auth);
        return blockService.getBlockList(me.getId());
    }

    /** GET /api/block/{userId}/status — blocked hai ya nahi */
    @GetMapping("/{userId}/status")
    public Map<String, Object> status(@PathVariable Long userId, Authentication auth) {
        User me = getUser(auth);
        boolean blocked = blockService.isBlocked(me.getId(), userId);
        return Map.of("blocked", blocked);
    }

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}