package com.yogesh.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class FanoutService {

	private final StringRedisTemplate redisTemplate; // FIX: StringRedisTemplate

	public void pushToFollowers(Long creatorId, Long postId) {

		Set<String> followers = redisTemplate.opsForSet().members("user:" + creatorId + ":followers");

		if (followers == null || followers.isEmpty())
			return;

		// Celebrity threshold — agar 10k+ followers to fanout skip karo
		if (followers.size() > 10_000)
			return;

		for (String follower : followers) {
			Long userId = Long.parseLong(follower);
			int shard = (int) (userId % 32);
			String key = "feed:shard:" + shard + ":user:" + userId;
			redisTemplate.opsForZSet().add(key, String.valueOf(postId), 1.0);
		}
	}
}