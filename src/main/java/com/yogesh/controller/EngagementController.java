package com.yogesh.controller;

import com.yogesh.dto.EngagementRequest;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.EngagementService;
import com.yogesh.service.MonetizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/engagement")
@RequiredArgsConstructor
public class EngagementController {

	private final EngagementService engagementService;
	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final MonetizationService monetizationService;

	// ─── Generic Engagement (LIKE / SHARE / COMMENT) ─────────────────────────
	@PostMapping
	public Map<String, Object> engage(Authentication authentication, @Valid @RequestBody EngagementRequest request) {

		User user = getUser(authentication);
		request.setUserId(user.getId());

		// Core engagement process
		engagementService.process(request);

		// Monetization — creator ko earning do
		triggerMonetization(request);

		return Map.of("status", "success", "message", "Engagement recorded");
	}

	// ─── Watch Time Event ─────────────────────────────────────────────────────
	@PostMapping("/watch")
	public Map<String, Object> watch(Authentication authentication, @Valid @RequestBody EngagementRequest request) {

		User user = getUser(authentication);
		request.setUserId(user.getId());
		request.setType("WATCH");

		// Core engagement
		engagementService.process(request);

		// View earning
		triggerMonetization(request);

		return Map.of("status", "success", "message", "Watch event recorded");
	}

	// ─── Monetization trigger ─────────────────────────────────────────────────
	private void triggerMonetization(EngagementRequest request) {
		try {
			Post post = postRepository.findById(request.getPostId()).orElse(null);
			if (post == null || post.getUserId() == null)
				return;

			// Apni post pe khud engage karne se earning nahi milti
			if (post.getUserId().equals(request.getUserId()))
				return;

			Long creatorId = post.getUserId();

			switch (request.getType()) {
			case "WATCH" -> monetizationService.recordViewEarning(post.getId(), creatorId);
			case "LIKE" -> monetizationService.recordLikeEarning(post.getId(), creatorId);
			case "SHARE" -> monetizationService.recordShareEarning(post.getId(), creatorId);
			// COMMENT, BOOKMARK etc. ke liye abhi earning nahi
			}
		} catch (Exception e) {
			// Monetization fail hone pe engagement fail nahi hona chahiye
			log.warn("Monetization trigger fail: postId={} type={} err={}", request.getPostId(), request.getType(),
					e.getMessage());
		}
	}

	private User getUser(Authentication auth) {
		return userRepository.findByEmail(auth.getName()).orElseThrow();
	}
}