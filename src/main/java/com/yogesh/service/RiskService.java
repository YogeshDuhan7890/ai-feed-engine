package com.yogesh.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RiskService {

	private final StringRedisTemplate redis;

	private static final int USER_THRESHOLD = 7;
	private static final int IP_THRESHOLD = 5;
	private static final Duration[] IP_BAN_DURATIONS = new Duration[] {
			Duration.ofMinutes(30),
			Duration.ofHours(2),
			Duration.ofHours(24),
			Duration.ofDays(30)
	};
	private static final Duration[] USER_BAN_DURATIONS = new Duration[] {
			Duration.ofHours(6),
			Duration.ofHours(24),
			Duration.ofDays(7),
			Duration.ofDays(30)
	};

	public void recordViolation(Long userId, String ip, String reason) {

		Long u = redis.opsForValue().increment("user:strikes:" + userId);
		redis.expire("user:strikes:" + userId, Duration.ofHours(24));

		Long i = redis.opsForValue().increment("ip:strikes:" + ip);
		redis.expire("ip:strikes:" + ip, Duration.ofHours(1));

		if (i != null && i >= IP_THRESHOLD) {
			redis.opsForValue().set(
					"ban:ip:" + ip,
					"Auto ban: suspicious activity",
					nextBanDuration("ban:ip:count:" + ip, IP_BAN_DURATIONS, Duration.ofDays(30)));
		}

		if (u != null && u >= USER_THRESHOLD) {
			redis.opsForValue().set(
					"ban:user:" + userId,
					"Auto ban: repeated violations",
					nextBanDuration("ban:user:count:" + userId, USER_BAN_DURATIONS, Duration.ofDays(30)));
		}
	}

	private Duration nextBanDuration(String counterKey, Duration[] ladder, Duration counterTtl) {
		Long strikes = redis.opsForValue().increment(counterKey);
		redis.expire(counterKey, counterTtl);
		int index = strikes == null ? 0 : (int) Math.max(0, Math.min(strikes - 1, ladder.length - 1));
		return ladder[index];
	}
}
