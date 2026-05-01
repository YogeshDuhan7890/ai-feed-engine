package com.yogesh.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

import com.yogesh.util.RedisKeys;

@Service
@RequiredArgsConstructor
public class ColdStartService {

	private final StringRedisTemplate redisTemplate; // FIX: StringRedisTemplate

	public Set<String> getTrendingFallback(int size) {
		return redisTemplate.opsForZSet().reverseRange(RedisKeys.FEED_GLOBAL_TRENDING_ZSET, 0, size - 1);
	}
}