package com.yogesh.service;

import com.yogesh.dto.EngagementRequest;
import com.yogesh.event.EngagementEvent;
import com.yogesh.model.Engagement;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.EngagementRepository;
import com.yogesh.repository.PostRepository;

import lombok.RequiredArgsConstructor;
import com.yogesh.repository.UserRepository;
import com.yogesh.util.RedisKeys;

import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EngagementService {

	private final EngagementRepository engagementRepository;
	private final StringRedisTemplate redisTemplate;
	private final KafkaTemplate<String, EngagementEvent> kafkaTemplate;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;
	private final EmailService emailService;
	private final UserEmbeddingService userEmbeddingService;
	private final RateLimitService rateLimitService;
	private final CacheInvalidationService cacheInvalidationService;

	@Value("${app.kafka.topics.feed:feed-topic}")
	private String feedTopic;

	@PreAuthorize("hasRole('USER')")
	@Transactional
	public void process(EngagementRequest request) {

		validateRequest(request);
		applyRateLimit(request.getUserId());
		if ("LIKE".equals(request.getType())) {
			try { handleLikeToggle(request); } catch (RuntimeException e) {
				if ("UNLIKED".equals(e.getMessage())) return;
			}
		}

		Post post = postRepository.findById(request.getPostId())
				.orElseThrow(() -> new RuntimeException("Post not found"));

		boolean saved = saveEngagement(request);
		if (saved) {
			updatePostCounters(request);
			cacheInvalidationService.postChanged(request.getPostId());
		}
		updateInvertedIndex(request);
		updateWatchTime(post, request);
		updateWatchCompletion(request, post);
		updateUserInterest(request.getUserId(), post);
		updateUserVector(request.getUserId(), post);
		updateCreatorAffinity(request.getUserId(), post);
		updateRewatch(request, post);
		updateTrendingScore(post, request);
		updateSkipSignal(request, post);
		updateVelocity(post, request);
		triggerNotification(request, post);

		if ("WATCH".equals(request.getType()) || "LIKE".equals(request.getType())) {
			try {
				userEmbeddingService.updateUserVector(request.getUserId());
			} catch (Exception ignored) {
			}
		}

		publishKafkaEvent(request);
	}

	// =====================
	// NOTIFICATION TRIGGER
	// =====================

	private void triggerNotification(EngagementRequest request, Post post) {
		if (post.getUserId() == null)
			return;
		if ("WATCH".equals(request.getType()))
			return;

		notificationService.create(post.getUserId(), request.getUserId(), request.getType(), post.getId(),
				request.getCommentText());

		// Email notification (async — won't block feed)
		try {
			User postOwner = userRepository.findById(post.getUserId()).orElse(null);
			User actor = userRepository.findById(request.getUserId()).orElse(null);
			if (postOwner == null || actor == null)
				return;
			if (postOwner.getId().equals(actor.getId()))
				return; // No self-notifications

			switch (request.getType()) {
			case "LIKE" ->
				emailService.sendLikeEmail(postOwner.getEmail(), postOwner.getName(), actor.getName(), post.getId());
			case "COMMENT" -> emailService.sendCommentEmail(postOwner.getEmail(), postOwner.getName(), actor.getName(),
					request.getCommentText(), post.getId());
			default -> {
			} // SHARE, BOOKMARK etc — no email for now
			}
		} catch (Exception e) {
			// Email errors should never break engagement flow
			org.slf4j.LoggerFactory.getLogger(EngagementService.class).warn("Email trigger failed: {}", e.getMessage());
		}
	}

	// =====================
	// SAVE ENGAGEMENT
	// =====================

	// FIX: PostgreSQL partition error handle karo
	// "no partition of relation engagements found for row" — March 2026 ka
	// partition nahi tha
	// Solution 1 (permanent): SQL migration se partition banao —
	// engagement_partitions.sql
	// Solution 2 (safety net): Partition error pe quietly log karo — rest of
	// engagement pipeline rok mat
	// ── Like toggle — idempotent ──────────────────────────────────
	private void handleLikeToggle(EngagementRequest request) {
		if (!"LIKE".equals(request.getType())) return;
		// Already liked? → unlike
		boolean alreadyLiked = engagementRepository.existsLikeByUserAndPost(
			request.getUserId(), request.getPostId());
		if (alreadyLiked) {
			engagementRepository.deleteLike(request.getUserId(), request.getPostId());
			postRepository.decrementLikeCount(request.getPostId());
			redisTemplate.opsForValue().decrement("post:" + request.getPostId() + ":likes");
			cacheInvalidationService.postChanged(request.getPostId());
			throw new RuntimeException("UNLIKED"); // signal to stop further processing
		}
	}

	private boolean saveEngagement(EngagementRequest request) {
		try {
			Engagement e = new Engagement();
			e.setUserId(request.getUserId());
			e.setPostId(request.getPostId());
			e.setType(request.getType());
			e.setWatchTime(request.getWatchTime());
			e.setCreatedAt(LocalDateTime.now());
			engagementRepository.save(e);
			return true;
		} catch (org.springframework.dao.DataIntegrityViolationException ex) {
			org.slf4j.LoggerFactory.getLogger(EngagementService.class)
					.warn("Engagement save skip — partition missing for current month. "
							+ "Run engagement_partitions.sql to fix permanently. Error: {}", ex.getMessage());
			return false;
		}
	}

	private void updatePostCounters(EngagementRequest request) {
		try {
			switch (request.getType()) {
			case "LIKE" -> {
				postRepository.incrementLikeCount(request.getPostId());
				redisTemplate.opsForValue().increment("post:" + request.getPostId() + ":likes");
			}
			case "WATCH" -> postRepository.incrementViewCount(request.getPostId());
			default -> {
			}
			}
		} catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger(EngagementService.class)
					.warn("Post counter update failed: postId={} type={} err={}", request.getPostId(),
							request.getType(), e.getMessage());
		}
	}

	private void updateInvertedIndex(EngagementRequest request) {
		redisTemplate.opsForSet().add("user:" + request.getUserId() + ":watched", String.valueOf(request.getPostId()));
		redisTemplate.opsForSet().add("post:" + request.getPostId() + ":watchers", String.valueOf(request.getUserId()));
	}

	private void updateWatchTime(Post post, EngagementRequest request) {
		if (request.getWatchTime() == null)
			return;
		redisTemplate.opsForZSet().incrementScore("post:watchtime", String.valueOf(post.getId()),
				request.getWatchTime());
	}

	private void updateWatchCompletion(EngagementRequest req, Post post) {
		if (req.getWatchTime() == null || post.getDuration() == null)
			return;
		double completion = req.getWatchTime() / post.getDuration();
		redisTemplate.opsForZSet().incrementScore("post:" + post.getId() + ":completion",
				String.valueOf(req.getUserId()), completion);
	}

	private void updateUserInterest(Long userId, Post post) {
		if (post.getTags() == null)
			return;
		String key = "user:" + userId + ":interests";
		for (String tag : post.getTags().split(",")) {
			redisTemplate.opsForZSet().incrementScore(key, tag.trim(), 1);
		}
	}

	private void updateUserVector(Long userId, Post post) {
		if (post.getEmbedding() == null)
			return;
		String key = "user:" + userId + ":vector";
		String existing = redisTemplate.opsForValue().get(key);

		if (existing == null) {
			redisTemplate.opsForValue().set(key, post.getEmbedding());
			return;
		}

		String[] userVec = existing.split(",");
		String[] postVec = post.getEmbedding().split(",");
		if (userVec.length != postVec.length)
			return;

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < userVec.length; i++) {
			double avg = (Double.parseDouble(userVec[i]) + Double.parseDouble(postVec[i])) / 2;
			sb.append(avg);
			if (i < userVec.length - 1)
				sb.append(",");
		}
		redisTemplate.opsForValue().set(key, sb.toString());
	}

	private void updateCreatorAffinity(Long userId, Post post) {
		if (post.getUserId() == null)
			return;
		redisTemplate.opsForZSet().incrementScore("user:" + userId + ":creators", String.valueOf(post.getUserId()), 1);
	}

	private void updateRewatch(EngagementRequest req, Post post) {
		if (req.getWatchTime() == null || post.getDuration() == null)
			return;
		if (req.getWatchTime() / post.getDuration() > 0.9) {
			redisTemplate.opsForZSet().incrementScore("post:" + post.getId() + ":rewatch",
					String.valueOf(req.getUserId()), 1);
		}
	}

	private void updateTrendingScore(Post post, EngagementRequest req) {
		double score = calculateScore(post, req);
		redisTemplate.opsForZSet().incrementScore(RedisKeys.TRENDING_GLOBAL_ZSET, String.valueOf(post.getId()),
				score);
	}

	private double calculateScore(Post post, EngagementRequest req) {
		double base = switch (req.getType()) {
		case "LIKE" -> 4;
		case "SHARE" -> 6;
		case "COMMENT" -> 5;
		case "WATCH" -> req.getWatchTime() != null ? req.getWatchTime() / 3.0 : 0;
		default -> 1;
		};
		if (post.getCreatedAt() == null)
			return base;
		long hours = Duration.between(post.getCreatedAt(), LocalDateTime.now()).toHours();
		return base / Math.pow(hours + 2, 1.4);
	}

	private void publishKafkaEvent(EngagementRequest request) {
		try {
			String key = String.valueOf(request.getUserId());
			EngagementEvent event = new EngagementEvent(request.getUserId(), request.getPostId(), request.getType(),
					request.getWatchTime());
			kafkaTemplate.send(feedTopic, key, event).whenComplete((result, ex) -> {
				if (ex != null) {
					org.slf4j.LoggerFactory.getLogger(EngagementService.class).warn(
							"Kafka send failed: topic={} key={} postId={} type={} error={}", feedTopic, key,
							request.getPostId(), request.getType(), ex.getMessage());
				}
			});
		} catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger(EngagementService.class).warn(
					"Kafka send could not be scheduled: topic={} userId={} postId={} type={} error={}", feedTopic,
					request.getUserId(), request.getPostId(), request.getType(), e.getMessage());
		}
	}

	private void applyRateLimit(Long userId) {
		try {
			var result = rateLimitService.consume("engagement", String.valueOf(userId),
					com.yogesh.config.RedisCachePolicy.ENGAGEMENT_RATE_LIMIT_PER_WINDOW,
					com.yogesh.config.RedisCachePolicy.ENGAGEMENT_RATE_LIMIT_WINDOW);
			if (!result.allowed()) {
				throw new RuntimeException("Too many requests");
			}
		} catch (DataAccessException ignored) {
		}
	}

	private void validateRequest(EngagementRequest request) {
		if (request.getUserId() == null || request.getPostId() == null || request.getType() == null)
			throw new IllegalArgumentException("Invalid engagement request");
	}

	// ── Skip signal — negative feedback ────────────────────────────
	private void updateSkipSignal(EngagementRequest request, Post post) {
		if (!"SKIP".equals(request.getType())) return;
		try {
			// Mark this post as skipped by user
			redisTemplate.opsForSet().add(
				"user:" + request.getUserId() + ":skipped",
				String.valueOf(post.getId()));
			// Track creator-level skips
			if (post.getUserId() != null) {
				redisTemplate.opsForValue().increment(
					"user:" + request.getUserId() + ":creator_skips:" + post.getUserId());
			}
		} catch (Exception e) { /* ignore */ }
	}

	// ── Velocity — pehle ghante ka engagement ───────────────────────
	private void updateVelocity(Post post, EngagementRequest request) {
		if (post.getCreatedAt() == null) return;
		long hours = java.time.Duration.between(post.getCreatedAt(), java.time.LocalDateTime.now()).toHours();
		if (hours > 1) return; // Only track first hour
		try {
			String key = "post:" + post.getId() + ":velocity";
			redisTemplate.opsForValue().increment(key);
			redisTemplate.expire(key, java.time.Duration.ofHours(2));
		} catch (Exception e) { /* ignore */ }
	}

}
