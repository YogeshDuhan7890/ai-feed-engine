package com.yogesh.service;

import com.yogesh.util.VectorUtil;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {

	private final StringRedisTemplate stringRedisTemplate;
	private final RedisTemplate<String, Object> objectRedisTemplate;

	// FIX: dono templates chahiye — user vector String, post embedding Object hash
	public VectorSearchService(StringRedisTemplate stringRedisTemplate,
			@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.objectRedisTemplate = objectRedisTemplate;
	}

	public double getSimilarity(Long userId, Long postId) {

		// User vector — StringRedisTemplate se (plain string value)
		String userVector = stringRedisTemplate.opsForValue().get("user:" + userId + ":vector");

		if (userVector == null)
			return 0;

		// Post embedding — objectRedisTemplate ke hash se
		Object postEmbedding = objectRedisTemplate.opsForHash().get("post:embedding", String.valueOf(postId));

		if (postEmbedding == null)
			return 0;

		return VectorUtil.cosineSimilarity(userVector, postEmbedding.toString());
	}
}