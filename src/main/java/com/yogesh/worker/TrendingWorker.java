package com.yogesh.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

import com.yogesh.util.RedisKeys;

/**
 * trending:global (raw scores) se top 200 le ke feed:global:trending (ranked
 * list for cold start) rebuild karo.
 *
 * TrendingDecayScheduler alag kaam karta hai — trending:global decay karta hai.
 * Yeh worker sirf feed:global:trending snapshot banata hai cold start ke liye.
 */
@Component
@RequiredArgsConstructor
public class TrendingWorker {

	private final StringRedisTemplate redisTemplate; // FIX: StringRedisTemplate

	@Scheduled(fixedDelay = 60_000)
	public void generateTrendingFeed() {

		// Top 200 trending posts le lo
		Set<String> ids = redisTemplate.opsForZSet().reverseRange(RedisKeys.TRENDING_GLOBAL_ZSET, 0, 199);

		if (ids == null || ids.isEmpty())
			return;

		// Purana snapshot delete karo
		redisTemplate.delete(RedisKeys.FEED_GLOBAL_TRENDING_ZSET);

		// Naya snapshot banao — score descending order mein
		int score = ids.size();
		for (String id : ids) {
			redisTemplate.opsForZSet().add(RedisKeys.FEED_GLOBAL_TRENDING_ZSET, id, score--);
		}
	}
}