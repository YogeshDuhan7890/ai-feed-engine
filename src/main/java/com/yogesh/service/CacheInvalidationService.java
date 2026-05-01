package com.yogesh.service;

import com.yogesh.config.RedisCachePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

	private final CacheManager cacheManager;
	private final PostCacheService postCacheService;
	private final StringRedisTemplate redisTemplate;

	public void postChanged(Long postId) {
		if (postId != null) {
			postCacheService.evict(postId);
			deleteKeys(List.of("post:" + postId + ":likes"));
		}
		clearCaches(RedisCachePolicy.POSTS, RedisCachePolicy.SEARCH, RedisCachePolicy.TRENDING,
				RedisCachePolicy.ANALYTICS, RedisCachePolicy.COMMENT_COUNTS);
	}

	public void commentChanged(Long postId) {
		if (postId != null) {
			postCacheService.evict(postId);
		}
		clearCaches(RedisCachePolicy.COMMENT_COUNTS, RedisCachePolicy.ANALYTICS, RedisCachePolicy.POSTS);
	}

	public void profileChanged(Long userId) {
		clearCaches(RedisCachePolicy.PROFILES, RedisCachePolicy.SUGGESTED);
	}

	public void searchIndexChanged() {
		clearCaches(RedisCachePolicy.SEARCH, RedisCachePolicy.TRENDING);
	}

	private void clearCaches(String... names) {
		for (String name : names) {
			try {
				Cache cache = cacheManager.getCache(name);
				if (cache != null) {
					cache.clear();
				}
			} catch (Exception ex) {
				log.debug("Cache clear skipped name={}: {}", name, ex.getMessage());
			}
		}
	}

	private void deleteKeys(Collection<String> keys) {
		try {
			redisTemplate.delete(keys);
		} catch (Exception ex) {
			log.debug("Redis key delete skipped keys={}: {}", keys, ex.getMessage());
		}
	}
}
