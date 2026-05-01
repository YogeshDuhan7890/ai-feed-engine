package com.yogesh.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

	private static final String KEY_PREFIX = "rate_limit:";

	private final StringRedisTemplate redisTemplate;

	public RateLimitResult consume(String scope, String identity, int limit, Duration window) {
		if (identity == null || identity.isBlank()) {
			identity = "anonymous";
		}

		String key = KEY_PREFIX + scope + ":" + sanitize(identity);
		long now = System.currentTimeMillis();
		long windowStart = now - window.toMillis();
		String member = now + ":" + UUID.randomUUID();

		try {
			redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
			Long count = redisTemplate.opsForZSet().zCard(key);

			if (count != null && count >= limit) {
				redisTemplate.expire(key, window.plusSeconds(5));
				return RateLimitResult.blocked(window);
			}

			redisTemplate.opsForZSet().add(key, member, now);
			redisTemplate.expire(key, window.plusSeconds(5));
			return RateLimitResult.allowedResult();
		} catch (Exception ex) {
			log.debug("Redis rate limit skipped for key={}: {}", key, ex.getMessage());
			return RateLimitResult.allowedResult();
		}
	}

	private String sanitize(String value) {
		return value.trim().toLowerCase().replaceAll("[^a-z0-9@._:-]", "_");
	}

	public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
		public static RateLimitResult allowedResult() {
			return new RateLimitResult(true, 0);
		}

		public static RateLimitResult blocked(Duration window) {
			return new RateLimitResult(false, Math.max(1, window.toSeconds()));
		}
	}
}
