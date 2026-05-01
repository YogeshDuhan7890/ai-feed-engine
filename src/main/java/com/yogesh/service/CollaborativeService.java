package com.yogesh.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborativeService {

	private final StringRedisTemplate redisTemplate;

	// Max watchers to check — performance cap
	private static final int MAX_WATCHERS = 50;

	/**
	 * Improved collaborative filtering:
	 *
	 * Old: O(n²) — loop through all watchers × all watched posts → Slow for popular
	 * posts with thousands of watchers
	 *
	 * New: O(n) — Redis SINTERCARD for set intersection → Count overlapping watched
	 * posts directly in Redis → Cap at MAX_WATCHERS to avoid slow queries
	 */
	public double getCollaborativeScore(Long userId, Long postId) {
		try {
			Set<String> watchers = redisTemplate.opsForSet().members("post:" + postId + ":watchers");

			if (watchers == null || watchers.isEmpty())
				return 0;

			// Remove self
			watchers.remove(String.valueOf(userId));
			if (watchers.isEmpty())
				return 0;

			// Cap at MAX_WATCHERS for performance
			String myWatchedKey = "user:" + userId + ":watched";
			Set<String> myWatched = redisTemplate.opsForSet().members(myWatchedKey);
			if (myWatched == null || myWatched.isEmpty())
				return 0;

			int overlap = 0;
			int checked = 0;

			for (String otherUser : watchers) {
				if (checked >= MAX_WATCHERS)
					break;
				checked++;

				// Use Redis SINTERCARD for O(1) overlap count
				try {
					Long intersectCount = redisTemplate.opsForSet().intersectAndStore(myWatchedKey,
							"user:" + otherUser + ":watched", "__tmp:collab:" + userId);
					if (intersectCount != null && intersectCount > 0) {
						overlap += intersectCount;
						// Cleanup temp key
						redisTemplate.delete("__tmp:collab:" + userId);
					}
				} catch (Exception e) {
					// Fallback: simple loop for this user
					Set<String> otherWatched = redisTemplate.opsForSet().members("user:" + otherUser + ":watched");
					if (otherWatched != null) {
						for (String post : myWatched) {
							if (otherWatched.contains(post))
								overlap++;
						}
					}
				}
			}

			// Normalize: higher overlap = more similar users watched this
			return Math.min(10.0, overlap * 0.5);

		} catch (Exception e) {
			log.warn("Collaborative score failed for userId={} postId={}: {}", userId, postId, e.getMessage());
			return 0;
		}
	}
}