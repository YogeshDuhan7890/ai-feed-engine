package com.yogesh.controller;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.ModerationService;
import com.yogesh.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;

    /** GET /api/polls/post/{postId} */
    @GetMapping("/post/{postId}")
    public Map<String, Object> getForPost(@PathVariable Long postId, Authentication auth) {
        User me = me(auth);
        return pollService.getPollForPost(postId, me != null ? me.getId() : null);
    }

    /**
     * POST /api/polls/post/{postId}
     * body: { "question": "...", "options": ["A","B"], "expiresInHours": 24 }
     */
    @PostMapping("/post/{postId}")
    public Map<String, Object> create(@PathVariable Long postId, @RequestBody Map<String, Object> body, Authentication auth) {
        User me = me(auth);
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        if (post.getUserId() == null || !post.getUserId().equals(me.getId())) {
            return Map.of("success", false, "message", "Sirf apni post pe poll add kar sakte ho");
        }

        String question = body != null && body.get("question") != null ? body.get("question").toString() : null;
        if (question != null && !question.isBlank()) {
            moderationService.assertAllowed(question, "Poll question");
        }

        @SuppressWarnings("unchecked")
        List<String> options = body != null && body.get("options") instanceof List ? (List<String>) body.get("options") : List.of();
        for (String opt : options) {
            if (opt != null && !opt.isBlank()) {
                moderationService.assertAllowed(opt, "Poll option");
            }
        }

        Integer expiresInHours = null;
        try {
            if (body != null && body.get("expiresInHours") != null) {
                expiresInHours = Integer.parseInt(body.get("expiresInHours").toString());
            }
        } catch (Exception ignored) {}

        return pollService.createPoll(postId, question, options, expiresInHours);
    }

    /**
     * POST /api/polls/post/{postId}/vote
     * body: { "optionId": 123 }
     */
    @PostMapping("/post/{postId}/vote")
    public Map<String, Object> vote(@PathVariable Long postId, @RequestBody Map<String, Object> body, Authentication auth) {
        User me = me(auth);
        Long optionId;
        try {
            optionId = body != null && body.get("optionId") != null ? Long.parseLong(body.get("optionId").toString()) : null;
        } catch (Exception e) {
            optionId = null;
        }
        if (optionId == null || optionId <= 0) {
            return Map.of("success", false, "message", "optionId required");
        }
        return pollService.vote(postId, optionId, me.getId());
    }

    private User me(Authentication auth) {
        if (auth == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }
}

