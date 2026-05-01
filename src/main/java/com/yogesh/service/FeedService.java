package com.yogesh.service;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.yogesh.util.RedisKeys;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

	private final PostRepository postRepository;
	private final FeedRankingService feedRankingService;
	private final UserRepository userRepository;
	private final FollowRepository followRepository;
	private final StringRedisTemplate redisTemplate;

	// ── Config ────────────────────────────────────────────────────
	private static final int FETCH_MULTIPLIER = 5;
	private static final int MAX_SAME_CREATOR = 2; // Max 2 videos per creator per batch
	private static final int FOLLOWING_PCT = 40; // 40% from following
	private static final int INTEREST_PCT = 35; // 35% interest/trending
	private static final int EXPLORE_PCT = 25; // 25% cold/new content

	/**
	 * ═══════════════════════════════════════════════════════════ UPGRADED FEED
	 * ALGORITHM ═══════════════════════════════════════════════════════════
	 *
	 * 3-layer feed (Instagram + TikTok best of both):
	 *
	 * Layer 1 — FOLLOWING (40%) Videos from people user follows, ranked by
	 * engagement Instagram jaisa: relationships matter
	 *
	 * Layer 2 — INTEREST (35%) Videos matching user's interest tags + trending
	 * YouTube jaisa: topics user loves
	 *
	 * Layer 3 — EXPLORE (25%) New creators, trending globally, cold start TikTok
	 * jaisa: discover new content
	 *
	 * Each layer scored by FeedRankingService Creator diversity enforced (max 2 per
	 * creator) Watched posts filtered (with auto-reset)
	 */
	public Map<String, Object> getHybridFeed(Long userId, String cursor, int size) {

		int fetchSize = size * FETCH_MULTIPLIER;

		// ── Watched set ──────────────────────────────────────────────
		Set<String> watched = getWatchedSet(userId);

		// ── Following IDs ────────────────────────────────────────────
		Set<Long> followingIds = getFollowingIds(userId);

		// ── Layer 1: Following feed (40%) ─────────────────────────────
		int followingTarget = (int) Math.ceil(size * FOLLOWING_PCT / 100.0);
		List<Post> followingPosts = getFollowingPosts(followingIds, watched, fetchSize, cursor);
		List<Post> followingRanked = rankAndLimit(userId, followingPosts, followingTarget);

		// ── Layer 2: Interest/Trending feed (35%) ─────────────────────
		int interestTarget = (int) Math.ceil(size * INTEREST_PCT / 100.0);
		Set<Long> alreadyAdded = followingRanked.stream().map(Post::getId).collect(Collectors.toSet());
		List<Post> interestPosts = getInterestPosts(userId, watched, alreadyAdded, fetchSize, cursor);
		List<Post> interestRanked = rankAndLimit(userId, interestPosts, interestTarget);
		alreadyAdded.addAll(interestRanked.stream().map(Post::getId).collect(Collectors.toSet()));

		// ── Layer 3: Explore/Cold-start (25%) ─────────────────────────
		int exploreTarget = size - followingRanked.size() - interestRanked.size();
		List<Post> explorePosts = getExplorePosts(watched, alreadyAdded, fetchSize, cursor);
		List<Post> exploreRanked = rankAndLimit(userId, explorePosts, Math.max(1, exploreTarget));

		// ── Merge all 3 layers ────────────────────────────────────────
		List<Post> merged = new ArrayList<>();
		merged.addAll(followingRanked);
		merged.addAll(interestRanked);
		merged.addAll(exploreRanked);

		// If feed too small — fallback to DB latest
		if (merged.size() < size / 2) {
			log.info("Feed too small ({}), fetching DB fallback for userId={}", merged.size(), userId);
			merged = getFallbackPosts(fetchSize, cursor);
			merged = rankAndLimit(userId, merged, size);
		}

		// ── Creator diversity ─────────────────────────────────────────
		List<Post> diverse = applyCreatorDiversity(merged, size);

		// ── Build response ─────────────────────────────────────────────
		Set<Long> userIds = diverse.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		List<Map<String, Object>> feed = new ArrayList<>();
		for (Post p : diverse) {
			User u = p.getUserId() != null ? userMap.get(p.getUserId()) : null;
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("postId", p.getId());
			item.put("videoUrl", p.getVideoUrl());
			item.put("thumbnailUrl", p.getThumbnailUrl() != null ? p.getThumbnailUrl() : "");
			item.put("content", p.getContent() != null ? p.getContent() : "");
			item.put("tags", p.getTags() != null ? p.getTags() : "");
			item.put("chapters", p.getChapters());
			item.put("parentPostId", p.getParentPostId());
			item.put("userId", p.getUserId());
			item.put("userName", u != null ? u.getName() : "User");
			item.put("userAvatar", u != null ? u.getAvatar() : null);
			item.put("createdAt", p.getCreatedAt());
			item.put("isFollowing", followingIds.contains(p.getUserId()));
			item.put("likeCount", p.getLikeCount());
			item.put("viewCount", p.getViewCount());
			item.put("likes", p.getLikeCount());
			item.put("views", p.getViewCount());
			feed.add(item);
		}

		String nextCursor = diverse.isEmpty() ? null : String.valueOf(diverse.get(diverse.size() - 1).getId());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("feed", feed);
		result.put("data", feed); // legacy compatibility
		result.put("nextCursor", nextCursor);
		result.put("size", feed.size());
		return result;
	}

	// ── Layer 1: Posts from followed users ────────────────────────
	private List<Post> getFollowingPosts(Set<Long> followingIds, Set<String> watched, int limit, String cursor) {
		if (followingIds.isEmpty())
			return Collections.emptyList();
		try {
			List<Post> all;
			if (cursor != null && !cursor.isBlank()) {
				try {
					long cursorId = Long.parseLong(cursor);
					all = postRepository.findByUserIdInBeforeCursor(new ArrayList<>(followingIds), cursorId,
							PageRequest.of(0, limit));
				} catch (Exception e) {
					all = postRepository.findByUserIdIn(new ArrayList<>(followingIds), PageRequest.of(0, limit));
				}
			} else {
				all = postRepository.findByUserIdIn(new ArrayList<>(followingIds), PageRequest.of(0, limit));
			}
			return filterValid(all, watched);
		} catch (Exception e) {
			log.warn("Following posts fetch failed: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	// ── Layer 2: Interest-based posts ─────────────────────────────
	private List<Post> getInterestPosts(Long userId, Set<String> watched, Set<Long> exclude, int limit, String cursor) {
		try {
			// Get user's top interest tags from Redis
			Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> interestTags = redisTemplate
					.opsForZSet().reverseRangeWithScores("user:" + userId + ":interests", 0, 4);

			List<Post> all;
			if (cursor != null && !cursor.isBlank()) {
				try {
					long cursorId = Long.parseLong(cursor);
					all = postRepository.findBeforeCursor(cursorId, PageRequest.of(0, limit));
				} catch (Exception e) {
					all = postRepository.findLatestVideos(PageRequest.of(0, limit));
				}
			} else {
				all = postRepository.findLatestVideos(PageRequest.of(0, limit));
			}

			// Filter by interest tags if available
			if (interestTags != null && !interestTags.isEmpty()) {
				Set<String> tags = interestTags.stream().map(t -> t.getValue().toLowerCase())
						.collect(Collectors.toSet());
				List<Post> tagged = all.stream().filter(p -> p.getTags() != null
						&& Arrays.stream(p.getTags().split(",")).anyMatch(t -> tags.contains(t.trim().toLowerCase())))
						.collect(Collectors.toList());
				if (!tagged.isEmpty())
					all = tagged;
			}

			return filterValid(all, watched).stream().filter(p -> !exclude.contains(p.getId()))
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.warn("Interest posts fetch failed: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	// ── Layer 3: Explore — new/trending content ───────────────────
	private List<Post> getExplorePosts(Set<String> watched, Set<Long> exclude, int limit, String cursor) {
		try {
			// Mix: trending + newest
			List<Post> trending = getTrendingPosts(limit / 2);
			List<Post> newest;
			if (cursor != null && !cursor.isBlank()) {
				try {
					long cursorId = Long.parseLong(cursor);
					newest = postRepository.findBeforeCursor(cursorId, PageRequest.of(0, limit / 2));
				} catch (Exception e) {
					newest = postRepository.findLatestVideos(PageRequest.of(0, limit / 2));
				}
			} else {
				newest = postRepository.findLatestVideos(PageRequest.of(0, limit / 2));
			}

			List<Post> combined = new ArrayList<>(trending);
			combined.addAll(newest);
			Collections.shuffle(combined, new Random()); // Randomize explore

			return filterValid(combined, watched).stream().filter(p -> !exclude.contains(p.getId()))
					.collect(Collectors.toList());
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	// ── Trending posts from Redis ─────────────────────────────────
	private List<Post> getTrendingPosts(int limit) {
		try {
			Set<String> trendingIds = redisTemplate.opsForZSet()
					.reverseRange(RedisKeys.TRENDING_GLOBAL_ZSET, 0, limit - 1);
			if (trendingIds == null || trendingIds.isEmpty())
				return Collections.emptyList();
			List<Long> ids = trendingIds.stream().map(Long::parseLong).collect(Collectors.toList());
			return postRepository.findAllById(ids);
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	// ── DB fallback ───────────────────────────────────────────────
	private List<Post> getFallbackPosts(int limit, String cursor) {
		try {
			if (cursor != null && !cursor.isBlank()) {
				long cursorId = Long.parseLong(cursor);
				return postRepository.findBeforeCursor(cursorId, PageRequest.of(0, limit));
			}
			return postRepository.findLatestVideos(PageRequest.of(0, limit));
		} catch (Exception e) {
			return postRepository.findLatestVideos(PageRequest.of(0, limit));
		}
	}

	// ── Rank and limit ────────────────────────────────────────────
	private List<Post> rankAndLimit(Long userId, List<Post> posts, int limit) {
		return posts.stream().map(p -> {
			double score;
			try {
				score = feedRankingService.score(userId, p);
			} catch (Exception e) {
				score = 0;
			}
			return new AbstractMap.SimpleEntry<>(p, score);
		}).sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(limit).map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}

	// ── Creator diversity ─────────────────────────────────────────
	private List<Post> applyCreatorDiversity(List<Post> posts, int limit) {
		Map<Long, Integer> creatorCount = new HashMap<>();
		List<Post> result = new ArrayList<>();
		List<Post> overflow = new ArrayList<>();

		for (Post p : posts) {
			if (result.size() >= limit)
				break;
			Long cid = p.getUserId();
			if (cid == null) {
				result.add(p);
				continue;
			}
			int count = creatorCount.getOrDefault(cid, 0);
			if (count < MAX_SAME_CREATOR) {
				result.add(p);
				creatorCount.put(cid, count + 1);
			} else {
				overflow.add(p);
			}
		}
		// Fill remaining spots from overflow
		for (Post p : overflow) {
			if (result.size() >= limit)
				break;
			result.add(p);
		}
		return result;
	}

	// ── Filter helpers ────────────────────────────────────────────
	private List<Post> filterValid(List<Post> posts, Set<String> watched) {
		Set<Long> hiddenIds = getHiddenPostIds();
		LocalDateTime now = LocalDateTime.now();
		return posts.stream().filter(p -> p.getVideoUrl() != null && !p.getVideoUrl().isBlank())
				.filter(p -> !watched.contains(String.valueOf(p.getId())))
				.filter(p -> isPostEligible(p, now))
				.filter(p -> !hiddenIds.contains(p.getId()))
				.collect(Collectors.toList());
	}

	private boolean isPostEligible(Post p, LocalDateTime now) {
		if (p == null) return false;
		// Safety: if scheduledAt exists in future, treat as scheduled.
		if (p.getScheduledAt() != null && p.getScheduledAt().isAfter(now)) {
			return false;
		}
		String st = p.getStatus();
		if (st != null && "SCHEDULED".equalsIgnoreCase(st)) {
			// Show only when scheduledAt is due (or already in past).
			return p.getScheduledAt() != null && !p.getScheduledAt().isAfter(now);
		}
		return true; // PUBLISHED (or unknown status) fallback
	}

	public Set<Long> getHiddenPostIds() {
		Set<Long> hiddenIds = new HashSet<>();
		try {
			Set<String> hiddenSet = redisTemplate.opsForSet().members(RedisKeys.ADMIN_HIDDEN_POSTS_SET);
			if (hiddenSet != null) {
				for (String raw : hiddenSet) {
					try {
						hiddenIds.add(Long.parseLong(raw));
					} catch (Exception ignored) {
					}
				}
			}

			// Legacy support: ab hidden posts runtime me KEYS() scan se load nahi hote.
		} catch (Exception e) {
			log.warn("Hidden post read failed: {}", e.getMessage());
		}
		return hiddenIds;
	}

	public boolean isHiddenPost(Long postId) {
		return postId != null && getHiddenPostIds().contains(postId);
	}

	private Set<Long> getFollowingIds(Long userId) {
		try {
			return followRepository.findFollowingIdsByFollowerId(userId);
		} catch (Exception e) {
			return Collections.emptySet();
		}
	}

	private Set<String> getWatchedSet(Long userId) {
		try {
			Set<String> watched = redisTemplate.opsForSet().members("user:" + userId + ":watched");
			return watched != null ? watched : Collections.emptySet();
		} catch (Exception e) {
			return Collections.emptySet();
		}
	}

	private Map<String, Object> emptyFeed() {
		Map<String, Object> result = new HashMap<>();
		result.put("feed", Collections.emptyList());
		result.put("data", Collections.emptyList());
		result.put("nextCursor", null);
		result.put("size", 0);
		return result;
	}
}
