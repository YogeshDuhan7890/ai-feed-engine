package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.yogesh.config.RedisCachePolicy.ANALYTICS;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

	private final AnalyticsService analyticsService;
	private final UserRepository userRepository;

	/**
	 * GET /api/analytics/dashboard Cache 10 min — analytics baar baar nahi badlti
	 */
	@GetMapping("/dashboard")
	@Cacheable(value = ANALYTICS, key = "'dashboard:' + #auth.name", unless = "#result == null")
	public Map<String, Object> dashboard(Authentication auth) {
		User me = userRepository.findByEmail(auth.getName()).orElseThrow();
		return analyticsService.getDashboard(me.getId());
	}

	/**
	 * GET /api/analytics/post/{postId} Cache 5 min per post
	 */
	@GetMapping("/post/{postId}")
	@Cacheable(value = ANALYTICS, key = "'post:' + #postId", unless = "#result == null")
	public Map<String, Object> post(@PathVariable Long postId, Authentication auth) {
		User me = userRepository.findByEmail(auth.getName()).orElseThrow();
		return analyticsService.getPostAnalytics(postId, me.getId());
	}

	/** GET /api/analytics/follower-growth */
	@GetMapping("/follower-growth")
	public java.util.List<java.util.Map<String, Object>> followerGrowth(Authentication auth) {
		User me = userRepository.findByEmail(auth.getName()).orElseThrow();
		return analyticsService.getFollowerGrowth(me.getId());
	}

	/** GET /api/analytics/videos */
	@GetMapping("/videos")
	public java.util.List<java.util.Map<String, Object>> videoPerformance(Authentication auth) {
		User me = userRepository.findByEmail(auth.getName()).orElseThrow();
		return analyticsService.getVideoPerformance(me.getId());
	}

	/**
	 * POST /api/analytics/refresh — force cache evict
	 */
	@PostMapping("/refresh")
	@CacheEvict(value = ANALYTICS, allEntries = true)
	public Map<String, String> refresh() {
		return Map.of("status", "cache cleared");
	}
}
