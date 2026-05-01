package com.yogesh.service;

import com.yogesh.model.Post;
import com.yogesh.repository.PostRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedRefillService {

	private final PostRepository postRepository;
	private final FeedRankingService rankingService;
	private final StringRedisTemplate redisTemplate; // FIX: StringRedisTemplate

	public void refillFeed(Long userId) {

		int shard = (int) (userId % 32);
		String key = "feed:shard:" + shard + ":user:" + userId;

		Long size = redisTemplate.opsForZSet().size(key);
		if (size != null && size > 50)
			return;

		List<Post> posts = postRepository.findTop200ByOrderByCreatedAtDesc();

		for (Post post : posts) {
			double score = rankingService.score(userId, post);
			redisTemplate.opsForZSet().add(key, String.valueOf(post.getId()), score);
		}

		// FIX: removeRange(key, 0, -201) galat tha — poori list delete ho jaati thi
		// Correct: top 200 rakhne ke liye lowest scoring entries hataao
		Long total = redisTemplate.opsForZSet().size(key);
		if (total != null && total > 200) {
			// Remove lowest scoring entries (index 0 to total-201)
			redisTemplate.opsForZSet().removeRange(key, 0, total - 201);
		}
	}
}