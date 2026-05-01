package com.yogesh.controller;

import com.yogesh.dto.SearchResultDTO;
import com.yogesh.model.Hashtag;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.HashtagRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.SearchService;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.yogesh.config.RedisCachePolicy.SEARCH;
import static com.yogesh.config.RedisCachePolicy.TRENDING;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchApiController {

	private final SearchService searchService;
	private final PostRepository postRepository;
	private final HashtagRepository hashtagRepository;
	private final UserRepository userRepository;

	/* ── Users search ──────────────────────────────────────────────── */
	@GetMapping("/users")
	public List<SearchResultDTO.UserSearchDTO> searchUsers(@RequestParam String q) {
		return searchService.searchUsers(q);
	}

	/* ── Videos search — improved ──────────────────────────────────── */
	@GetMapping("/videos")
	public List<Map<String, Object>> searchVideos(@RequestParam String q, @RequestParam(defaultValue = "20") int size) {

		if (q == null || q.trim().length() < 2)
			return List.of();

		String query = q.trim();
		// Use paginated search
		List<Post> posts = postRepository.searchByContentOrTags(query, PageRequest.of(0, Math.min(size, 50)));

		if (posts.isEmpty())
			return List.of();

		// Batch fetch users — no N+1
		Set<Long> userIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		return posts.stream().map(p -> {
			User u = p.getUserId() != null ? userMap.get(p.getUserId()) : null;
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", p.getId());
			m.put("postId", p.getId());
			m.put("caption", p.getContent() != null ? p.getContent() : "");
			m.put("tags", p.getTags() != null ? p.getTags() : "");
			m.put("videoUrl", p.getVideoUrl());
			m.put("createdAt", p.getCreatedAt());
			m.put("userName", u != null ? u.getName() : "User");
			m.put("userAvatar", u != null ? u.getAvatar() : null);
			m.put("userId", p.getUserId());
			return m;
		}).collect(Collectors.toList());
	}

	/* ── Hashtags search — fixed (tag not name) ────────────────────── */
	@GetMapping("/hashtags")
	@Cacheable(value = SEARCH, key = "'hashtags:' + #q")
	public List<Map<String, Object>> searchHashtags(@RequestParam String q) {
		if (q == null || q.trim().length() < 1)
			return List.of();
		String clean = q.trim().replace("#", "").toLowerCase();
		try {
			// FIX: findByTagContainingIgnoreCase — not findByNameContaining
			List<Hashtag> tags = hashtagRepository.findByTagContainingIgnoreCaseOrderByPostCountDesc(clean);
			return tags.stream().limit(20).map(h -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("tag", h.getTag()); // FIX: .getTag() not .getName()
				m.put("name", "#" + h.getTag());
				m.put("postCount", h.getPostCount());
				return m;
			}).collect(Collectors.toList());
		} catch (Exception e) {
			return List.of(Map.of("tag", clean, "name", "#" + clean, "postCount", 0));
		}
	}

	/* ── Unified search — all types in one call ─────────────────────── */
	@GetMapping("/all")
	public Map<String, Object> searchAll(@RequestParam String q, Authentication auth) {

		if (q == null || q.trim().length() < 2)
			return Map.of("users", List.of(), "videos", List.of(), "hashtags", List.of());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("query", q.trim());
		result.put("users", searchService.searchUsers(q));
		result.put("videos", searchVideos(q, 10));
		result.put("hashtags", searchHashtags(q));
		return result;
	}

	/* ── Suggestions for search input (fast, cached) ────────────────── */
	@GetMapping("/suggestions")
	@Cacheable(value = SEARCH, key = "'sug:' + #q")
	public List<Map<String, Object>> suggestions(@RequestParam String q) {
		if (q == null || q.trim().length() < 2)
			return List.of();
		String query = q.trim();
		List<Map<String, Object>> results = new ArrayList<>();

		// Top 3 users
		userRepository.searchByNameOrEmail(query, PageRequest.of(0, 3)).forEach(u -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("type", "user");
			m.put("id", u.getId());
			m.put("label", u.getName());
			m.put("sub", u.getEmail());
			m.put("avatar", u.getAvatar());
			results.add(m);
		});

		// Top 3 hashtags
		hashtagRepository.findByTagContainingIgnoreCaseOrderByPostCountDesc(query.replace("#", "").toLowerCase())
				.stream().limit(3).forEach(h -> {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("type", "hashtag");
					m.put("label", "#" + h.getTag());
					m.put("sub", h.getPostCount() + " videos");
					m.put("url", "/hashtag/" + h.getTag());
					results.add(m);
				});

		return results;
	}

	/* ── Trending searches (Redis-backed) ───────────────────────────── */
	@GetMapping("/trending")
	@Cacheable(value = TRENDING, key = "'searches'")
	public List<Map<String, Object>> trendingSearches() {
		// Get trending hashtags as search suggestions
		return hashtagRepository.findTopTrending(PageRequest.of(0, 10)).stream().map(h -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("tag", "#" + h.getTag());
			m.put("count", h.getPostCount());
			return m;
		}).collect(Collectors.toList());
	}
}
