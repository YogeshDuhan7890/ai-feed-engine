package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.SuggestedUsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestedUsersController {

    private final SuggestedUsersService suggestedUsersService;
    private final UserRepository userRepository;

    /** GET /api/suggestions?limit=5 */
    @GetMapping
    public List<Map<String, Object>> suggest(
            @RequestParam(defaultValue = "6") int limit,
            Authentication auth) {
        User me = userRepository.findByEmail(auth.getName()).orElseThrow();
        return suggestedUsersService.getSuggestions(me.getId(), limit);
    }
}