package com.yogesh.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

import com.yogesh.util.RedisKeys;

@Component
@RequiredArgsConstructor
public class TrendingDecayScheduler {

	private final StringRedisTemplate redisTemplate; // FIX: StringRedisTemplate

	private static final String KEY = RedisKeys.TRENDING_GLOBAL_ZSET;

    @Scheduled(fixedRate = 60_000)
    public void decayTrendingScores() {
        try {
            Set<String> posts = redisTemplate.opsForZSet().range(KEY, 0, -1);
            if (posts == null || posts.isEmpty()) {
                return;
            }

            for (String postId : posts) {
                Double score = redisTemplate.opsForZSet().score(KEY, postId);
                if (score == null) {
                    continue;
                }
                double newScore = score * 0.98;
                redisTemplate.opsForZSet().add(KEY, postId, newScore);
            }

        } catch (Exception e) {
        	// swallow — non-critical background task
        }
    }
}
