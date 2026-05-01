package com.yogesh.worker;

import com.yogesh.event.EngagementEvent;
import com.yogesh.model.Post;
import com.yogesh.repository.PostRepository;
import com.yogesh.service.FeedRankingService;
import com.yogesh.util.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedWorker {

	private final FeedRankingService rankingService;
	private final StringRedisTemplate redisTemplate; // FIX: StringRedisTemplate use karo
	private final PostRepository postRepository;

	@KafkaListener(topics = "${app.kafka.topics.feed:feed-topic}", groupId = "feed-group", containerFactory = "engagementKafkaListenerContainerFactory")
	public void handle(EngagementEvent event) {

		if (event == null || event.getUserId() == null || event.getPostId() == null || event.getType() == null) {
			throw new IllegalArgumentException("Invalid engagement event: " + event);
		}

		Long userId = event.getUserId();
		Long postId = event.getPostId();

		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new IllegalStateException("Post not found for engagement event: " + postId));

		// 🔥 PERSONALIZED FEED
		int shard = (int) (userId % 32);
		String key = "feed:shard:" + shard + ":user:" + userId;

		double score = rankingService.score(userId, post);
		redisTemplate.opsForZSet().add(key, String.valueOf(postId), score);

		// 🔥 GLOBAL TRENDING ALSO UPDATE
		double trendingBoost = switch (event.getType()) {
		case "LIKE" -> 4;
		case "COMMENT" -> 5;
		case "SHARE" -> 6;
		case "WATCH" -> event.getWatchTime() != null ? event.getWatchTime() / 3.0 : 1;
		default -> 1;
		};

		redisTemplate.opsForZSet().incrementScore(RedisKeys.TRENDING_GLOBAL_ZSET, String.valueOf(postId),
				trendingBoost);
		log.debug("Feed event processed: userId={}, postId={}, type={}", userId, postId, event.getType());
	}

//	@KafkaListener(topics = "feed-topic", groupId = "feed-group", containerFactory = "engagementKafkaListenerContainerFactory")
//	public void handle(EngagementEvent event) {
//
//		Long userId = event.getUserId();
//		Long postId = event.getPostId();
//
//		Post post = postRepository.findById(postId).orElse(null);
//		if (post == null)
//			return;
//
//		int shard = (int) (userId % 32);
//		String key = "feed:shard:" + shard + ":user:" + userId;
//
//		double score = rankingService.score(userId, post);
//		redisTemplate.opsForZSet().add(key, String.valueOf(postId), score);
//	}
}
