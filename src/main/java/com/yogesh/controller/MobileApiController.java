package com.yogesh.controller;

import com.yogesh.dto.ApiResponse;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FeedService;
import com.yogesh.service.PostCacheService;
import com.yogesh.service.RateLimitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.yogesh.util.RedisKeys;

import static com.yogesh.config.RedisCachePolicy.MOBILE_RATE_LIMIT_PER_WINDOW;
import static com.yogesh.config.RedisCachePolicy.MOBILE_RATE_LIMIT_WINDOW;

@Slf4j
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
public class MobileApiController {

	private static final int PAGE_SIZE = 10;

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final PostCacheService postCacheService;
	private final StringRedisTemplate redisTemplate;
	private final FeedService feedService;
	private final MessageSource messageSource;
	private final RateLimitService rateLimitService;

	// ========================
	// RATE LIMIT CHECK
	// ========================
	private boolean isRateLimited(Long userId) {
		return !rateLimitService.consume("mobile_api", String.valueOf(userId), MOBILE_RATE_LIMIT_PER_WINDOW,
				MOBILE_RATE_LIMIT_WINDOW).allowed();
	}

	private User getUser(Authentication auth) {
		if (auth == null)
			return null;
		return userRepository.findByEmail(auth.getName()).orElse(null);
	}

	// ========================
	// FEED
	// ========================
	@GetMapping("/feed")
	public ApiResponse<?> mobileFeed(Authentication auth, @RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String cursor) {
		User me = getUser(auth);
		if (me == null)
			return ApiResponse.error(msg("mobile.unauthorized", "Unauthorized"));
		if (isRateLimited(me.getId()))
			return ApiResponse.error(msg("mobile.rateLimit.exceeded", "Rate limit exceeded. 1 minute mein zyada requests."));

		try {
			size = Math.min(size, 20);

			List<Post> posts;
			if (cursor != null && !cursor.isBlank()) {
				final long cursorId;
				try {
					cursorId = Long.parseLong(cursor);
				} catch (Exception e) {
					return ApiResponse.error(msg("mobile.cursor.invalid", "Invalid cursor. Provide postId (number)."));
				}
				posts = postRepository.findBeforeCursor(cursorId, PageRequest.of(0, size + 1));
			} else {
				posts = postRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size + 1)).getContent();
			}

			Set<Long> hiddenIds = feedService.getHiddenPostIds();

			posts = posts.stream().filter(p -> p.getVideoUrl() != null && !p.getVideoUrl().isBlank())
					.filter(p -> !hiddenIds.contains(p.getId())).filter(this::isPostEligible).collect(Collectors.toList());

			boolean hasMore = posts.size() > size;
			List<Post> page = hasMore ? posts.subList(0, size) : posts;
			String nextCursor = hasMore && !page.isEmpty() ? String.valueOf(page.get(page.size() - 1).getId()) : null;

			// Batch fetch users
			Set<Long> uids = page.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
			Map<Long, User> userMap = new HashMap<>();
			userRepository.findAllById(uids).forEach(u -> userMap.put(u.getId(), u));

			// Batch fetch post counters from posts table
			List<Long> postIds = page.stream().map(Post::getId).collect(Collectors.toList());
			Map<Long, long[]> counters = batchCounters(postIds);

			// Watched posts
			Set<String> watched = Optional
					.ofNullable(redisTemplate.opsForSet().members("user:" + me.getId() + ":watched")).orElse(Set.of());

			List<Map<String, Object>> data = new ArrayList<>();
			for (Post p : page) {
				User u = userMap.get(p.getUserId());
				long[] counter = counters.getOrDefault(p.getId(), new long[] { 0, 0 });
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", p.getId());
				item.put("videoUrl", p.getVideoUrl());
				item.put("caption", p.getContent() != null ? p.getContent() : "");
				item.put("tags", p.getTags() != null ? p.getTags() : "");
				item.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
				item.put("chapters", p.getChapters());
				item.put("parentPostId", p.getParentPostId());
				item.put("author",
						Map.of("id", p.getUserId() != null ? p.getUserId() : 0, "name", u != null ? u.getName() : "",
								"avatar", u != null && u.getAvatar() != null ? u.getAvatar() : ""));
				item.put("likes", counter[0]);
				item.put("likeCount", counter[0]);
				item.put("views", counter[1]);
				item.put("viewCount", counter[1]);
				item.put("comments", 0L);
				item.put("watched", watched.contains(String.valueOf(p.getId())));
				data.add(item);
			}

			return ApiResponse.paged(data, nextCursor, hasMore);
		} catch (Exception e) {
			log.error("Mobile feed error", e);
			return ApiResponse.error(msg("mobile.feed.loadFail", "Feed load fail") + ": " + e.getMessage());
		}
	}

	// ========================
	// USER PROFILE (compact)
	// ========================
	@GetMapping("/profile/{userId}")
	public ApiResponse<?> getUserProfile(@PathVariable Long userId, Authentication auth) {
		User me = getUser(auth);
		if (me == null)
			return ApiResponse.error(msg("mobile.unauthorized", "Unauthorized"));

		try {
			User user = userRepository.findById(userId).orElse(null);
			if (user == null)
				return ApiResponse.error(msg("mobile.user.notFound", "User not found"));

			Set<Long> hiddenIds = feedService.getHiddenPostIds();
			long visibleCount = postRepository.countVisibleByUserId(userId, LocalDateTime.now());
			long hiddenOwnCount = hiddenIds.isEmpty() ? 0L : postRepository.countByUserIdAndIdIn(userId, hiddenIds);
			long postCount = Math.max(0L, visibleCount - hiddenOwnCount);

			Map<String, Object> profile = new LinkedHashMap<>();
			profile.put("id", user.getId());
			profile.put("name", user.getName());
			profile.put("bio", user.getBio() != null ? user.getBio() : "");
			profile.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
			profile.put("followers", followRepository.countByFollowingId(userId));
			profile.put("following", followRepository.countByFollowerId(userId));
			profile.put("postCount", postCount);
			profile.put("isMe", me.getId().equals(userId));

			return ApiResponse.ok(profile);
		} catch (Exception e) {
			return ApiResponse.error(msg("mobile.profile.loadFail", "Profile load fail") + ": " + e.getMessage());
		}
	}

	// ========================
	// USER POSTS (paginated)
	// ========================
	@GetMapping("/profile/{userId}/posts")
	public ApiResponse<?> getUserPosts(@PathVariable Long userId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "12") int size) {
		try {
			size = Math.min(size, 20);
			Page<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
			Set<Long> hiddenIds = feedService.getHiddenPostIds();
			List<Map<String, Object>> data = posts.getContent().stream().filter(this::isPostEligible).map(p -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", p.getId());
				item.put("videoUrl", p.getVideoUrl());
				item.put("thumbnail", p.getVideoUrl());
				item.put("caption", p.getContent() != null ? p.getContent() : "");
				item.put("chapters", p.getChapters());
				item.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
				return item;
			}).filter(item -> !hiddenIds.contains(((Number) item.get("id")).longValue())).collect(Collectors.toList());

			return ApiResponse.paged(data, null, posts.hasNext());
		} catch (Exception e) {
			return ApiResponse.error(msg("mobile.posts.loadFail", "Posts load fail") + ": " + e.getMessage());
		}
	}

	// ========================
	// SEARCH
	// ========================
	@GetMapping("/search")
	public ApiResponse<?> search(Authentication auth, @RequestParam String q,
			@RequestParam(defaultValue = "0") int page) {
		User me = getUser(auth);
		if (me == null)
			return ApiResponse.error(msg("mobile.unauthorized", "Unauthorized"));
		if (isRateLimited(me.getId()))
			return ApiResponse.error(msg("mobile.rateLimit.exceeded.generic", "Rate limit exceeded"));
		if (q == null || q.trim().length() < 2)
			return ApiResponse.error(msg("mobile.search.query.min", "Query min 2 chars"));

		try {
			redisTemplate.opsForZSet().incrementScore("search:queries", q.trim().toLowerCase(), 1);
			redisTemplate.opsForList().leftPush("search:recent", q.trim());
			redisTemplate.opsForList().trim("search:recent", 0, 99);

			List<Post> posts = postRepository.searchByContentOrTags(q.trim());
			int from = page * PAGE_SIZE;
			int to = Math.min(from + PAGE_SIZE, posts.size());
			boolean hasMore = to < posts.size();

			List<Map<String, Object>> data = posts.subList(Math.min(from, posts.size()), to).stream().map(p -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", p.getId());
				item.put("videoUrl", p.getVideoUrl());
				item.put("caption", p.getContent() != null ? p.getContent() : "");
				item.put("tags", p.getTags() != null ? p.getTags() : "");
				return item;
			}).collect(Collectors.toList());

			return ApiResponse.paged(data, hasMore ? String.valueOf(page + 1) : null, hasMore);
		} catch (Exception e) {
			return ApiResponse.error(msg("mobile.search.fail", "Search fail") + ": " + e.getMessage());
		}
	}

	// ========================
	// TRENDING
	// ========================
	@GetMapping("/trending")
	public ApiResponse<?> trending(@RequestParam(defaultValue = "20") int size) {
		try {
			Set<String> trendingIds = redisTemplate.opsForZSet()
					.reverseRange(RedisKeys.TRENDING_GLOBAL_ZSET, 0, size - 1);
			Set<Long> hiddenIds = feedService.getHiddenPostIds();
			if (trendingIds == null || trendingIds.isEmpty()) {
				List<Post> recent = postRepository.findTop200ByOrderByCreatedAtDesc().stream()
						.filter(p -> p.getVideoUrl() != null && !hiddenIds.contains(p.getId())).limit(size).collect(Collectors.toList());
				return ApiResponse.ok(recent.stream().map(p -> Map.of("id", p.getId(), "videoUrl", p.getVideoUrl(),
						"caption", p.getContent() != null ? p.getContent() : "")).collect(Collectors.toList()));
			}

			List<Long> ids = trendingIds.stream().map(id -> {
				try {
					return Long.parseLong(id);
				} catch (Exception e) {
					return null;
				}
			}).filter(Objects::nonNull).collect(Collectors.toList());

			Map<Long, Post> postMap = new HashMap<>();
			postRepository.findAllById(ids).forEach(p -> postMap.put(p.getId(), p));

			List<Map<String, Object>> result = ids.stream().map(postMap::get).filter(Objects::nonNull)
					.filter(p -> p.getVideoUrl() != null && !hiddenIds.contains(p.getId())).map(p -> {
						Map<String, Object> item = new LinkedHashMap<>();
						item.put("id", p.getId());
						item.put("videoUrl", p.getVideoUrl());
						item.put("caption", p.getContent() != null ? p.getContent() : "");
						return item;
					}).collect(Collectors.toList());

			return ApiResponse.ok(result);
		} catch (Exception e) {
			return ApiResponse.error(msg("mobile.trending.loadFail", "Trending load fail") + ": " + e.getMessage());
		}
	}

	private boolean isPostEligible(Post p) {
		if (p == null) return false;
		if (p.getScheduledAt() != null && p.getScheduledAt().isAfter(LocalDateTime.now())) {
		}
		String st = p.getStatus();
		if (st != null && "SCHEDULED".equalsIgnoreCase(st)) {
			return p.getScheduledAt() != null && !p.getScheduledAt().isAfter(LocalDateTime.now());
		}
		return true;
	}

	// ========================
	// BATCH COUNTER HELPER
	// ========================
	private Map<Long, long[]> batchCounters(List<Long> postIds) {
		Map<Long, long[]> result = new HashMap<>();
		if (postIds == null || postIds.isEmpty())
			return result;
		try {
			List<Object[]> rows = postRepository.findCountersByPostIds(postIds);
			rows.forEach(r -> {
				Long postId = ((Number) r[0]).longValue();
				long likes = ((Number) r[1]).longValue();
				long views = ((Number) r[2]).longValue();
				result.put(postId, new long[] { likes, views });
			});
		} catch (Exception e) {
			log.warn("Batch counter fetch fail: {}", e.getMessage());
		}
		return result;
	}

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
