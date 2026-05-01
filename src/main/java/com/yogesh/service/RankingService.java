package com.yogesh.service;

import com.yogesh.model.Post;
import com.yogesh.repository.EngagementRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor

/**
 * @deprecated Yeh class use nahi hoti.
 * Feed scoring ke liye {@link FeedRankingService} use karo.
 * Is class ko future mein delete kar dena chahiye.
 */
@Deprecated(since = "1.0", forRemoval = true)
public class RankingService {

	private final VectorSearchService vectorSearchService;
	private final CollaborativeService collaborativeService;
	private final EngagementRepository engagementRepository;
	private final RedisTemplate<String, String> redisTemplate;

	/*
	 * FINAL INSTAGRAM STYLE SCORE
	 */

	public double calculateFinalScore(Long userId, Post post) {

		double engagementScore = getEngagementScore(post);

		double watchScore = getWatchScore(post);

		double vectorScore = vectorSearchService.getSimilarity(userId, post.getId());

		double collaborativeScore = collaborativeService.getCollaborativeScore(userId, post.getId());

		double recencyScore = getRecencyBoost(post);

		return (engagementScore * 0.30) + (watchScore * 0.25) + (vectorScore * 0.20) + (collaborativeScore * 0.15)
				+ (recencyScore * 0.10);

	}

	/*
	 * ENGAGEMENT SCORE
	 */

	private double getEngagementScore(Post post) {

		long likes = engagementRepository.countLikes(post.getId());

		long comments = engagementRepository.countComments(post.getId());

		long shares = engagementRepository.countShares(post.getId());

		double score = (likes * 1.0) + (comments * 1.5) + (shares * 2.0);

		return normalize(score);

	}

	/*
	 * WATCH TIME SCORE
	 */

	private double getWatchScore(Post post) {

		Double watch = redisTemplate.opsForZSet().score("watch:time", post.getId().toString());

		if (watch == null)
			return 0;

		return normalize(watch);

	}

	/*
	 * RECENCY BOOST
	 */

	private double getRecencyBoost(Post post) {

		if (post.getCreatedAt() == null)
			return 0;

		long hours = Duration.between(post.getCreatedAt(), LocalDateTime.now()).toHours();

		return Math.exp(-hours / 24.0);

	}

	/*
	 * NORMALIZATION
	 */

	private double normalize(double value) {

		return Math.tanh(value / 100.0);

	}

}