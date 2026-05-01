package com.yogesh.controller;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FeedController {

	private final StringRedisTemplate redisTemplate;
	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final FeedService feedService;

	@PostMapping("/flush-cache")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, Object> flushCache() {
		try {
			var factory = redisTemplate.getConnectionFactory();
			if (factory != null) {
				factory.getConnection().serverCommands().flushDb();
			}
			return Map.of("status", "ok", "message", "Redis flush ho gaya!");
		} catch (Exception e) {
			log.debug("Suppressed: {}", e.getMessage());
			return Map.of("status", "error", "message", e.getMessage());
		}
	}

	@GetMapping("/hybrid")
	public Map<String, Object> feed(Authentication authentication,
			@RequestParam(defaultValue = "5") int size,
			@RequestParam(required = false) String cursor) {

		Map<String, Object> response = new HashMap<>();

		if (authentication == null) {
			response.put("data", new ArrayList<>());
			response.put("nextCursor", null);
			return response;
		}

		try {
			String email = authentication.getName();
			User user = userRepository.findByEmail(email).orElseThrow();

			size = Math.max(1, Math.min(size, 20));

			Map<String, Object> hybrid = feedService.getHybridFeed(user.getId(), cursor, size);
			List<Map<String, Object>> items = castFeedItems(hybrid.get("data"));
			if (!items.isEmpty()) {
				return hybrid;
			}

			log.info("Hybrid feed empty, serving latest-video fallback for userId={}", user.getId());

			List<Post> dbPosts = postRepository.findLatestVideos(PageRequest.of(0, size));
			List<Map<String, Object>> fallbackItems = buildFallbackItems(dbPosts);

			response.put("data", fallbackItems);
			response.put("feed", fallbackItems);
			response.put("nextCursor",
					fallbackItems.isEmpty() ? null : String.valueOf(fallbackItems.get(fallbackItems.size() - 1).get("postId")));
		} catch (Exception e) {
			log.error("Feed load error", e);
			response.put("data", new ArrayList<>());
			response.put("nextCursor", null);
			response.put("error", "Feed load nahi hui");
		}

		return response;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> castFeedItems(Object value) {
		if (value instanceof List<?> list) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : list) {
				if (item instanceof Map<?, ?> map) {
					items.add((Map<String, Object>) map);
				}
			}
			return items;
		}
		return List.of();
	}

	private List<Map<String, Object>> buildFallbackItems(List<Post> dbPosts) {
		Set<Long> hiddenIds = feedService.getHiddenPostIds();
		Set<Long> userIds = new HashSet<>();
		LocalDateTime now = LocalDateTime.now();
		for (Post post : dbPosts) {
			if (post == null || !isPostEligible(post, now) || hiddenIds.contains(post.getId())) {
				continue;
			}
			if (post.getUserId() != null) {
				userIds.add(post.getUserId());
			}
		}

		Map<Long, User> userMap = new HashMap<>();
		userRepository.findAllById(userIds).forEach(user -> userMap.put(user.getId(), user));

		List<Map<String, Object>> items = new ArrayList<>();
		for (Post post : dbPosts) {
			if (post == null || !isPostEligible(post, now) || post.getVideoUrl() == null || post.getVideoUrl().isBlank()
					|| hiddenIds.contains(post.getId())) {
				continue;
			}

			User author = post.getUserId() == null ? null : userMap.get(post.getUserId());

			Map<String, Object> item = new HashMap<>();
			item.put("postId", post.getId());
			item.put("videoUrl", post.getVideoUrl());
			item.put("content", post.getContent() != null ? post.getContent() : "");
			item.put("tags", post.getTags() != null ? post.getTags() : "");
			item.put("userId", post.getUserId());
			item.put("userName", author != null && author.getName() != null ? author.getName() : "user");
			item.put("userAvatar", author != null ? author.getAvatar() : null);
			item.put("thumbnailUrl", post.getThumbnailUrl() != null ? post.getThumbnailUrl() : "");
			item.put("chapters", post.getChapters());
			item.put("createdAt", post.getCreatedAt());
			item.put("likeCount", post.getLikeCount());
			item.put("viewCount", post.getViewCount());
			item.put("likes", post.getLikeCount());
			item.put("views", post.getViewCount());
			items.add(item);
		}

		return items;
	}

	private boolean isPostEligible(Post p, LocalDateTime now) {
		if (p == null) return false;
		if (p.getScheduledAt() != null && p.getScheduledAt().isAfter(now)) {
			return false;
		}
		String st = p.getStatus();
		if (st != null && "SCHEDULED".equalsIgnoreCase(st)) {
			return p.getScheduledAt() != null && !p.getScheduledAt().isAfter(now);
		}
		return true;
	}
}
