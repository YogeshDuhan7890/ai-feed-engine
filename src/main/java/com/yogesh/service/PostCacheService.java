package com.yogesh.service;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.yogesh.config.RedisCachePolicy.POST_ENGAGEMENT_TTL;
import static com.yogesh.config.RedisCachePolicy.POSTS_TTL;

@Slf4j
@Service
public class PostCacheService {

	private final RedisTemplate<String, Object> objectRedisTemplate;
	private final StringRedisTemplate stringRedisTemplate;
	private final PostRepository postRepository;
	private final UserRepository userRepository;

	public PostCacheService(@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate,
			StringRedisTemplate stringRedisTemplate, PostRepository postRepository, UserRepository userRepository) {
		this.objectRedisTemplate = objectRedisTemplate;
		this.stringRedisTemplate = stringRedisTemplate;
		this.postRepository = postRepository;
		this.userRepository = userRepository;
	}

	public Map<String, Object> getPost(Long postId) {
		String key = "post:" + postId;
		try {
			Map<Object, Object> cached = objectRedisTemplate.opsForHash().entries(key);
			if (cached != null && !cached.isEmpty()) {
				Map<String, Object> post = new HashMap<>();
				cached.forEach((k, v) -> post.put(k.toString(), v));
				return post;
			}
		} catch (Exception e) {
			log.warn("Cache read fail post {}: {}", postId, e.getMessage());
		}
		Post p = postRepository.findById(postId).orElse(null);
		if (p == null)
			return null;
		User user = p.getUserId() != null ? userRepository.findById(p.getUserId()).orElse(null) : null;
		return cachePost(p, user);
	}

	public List<Map<String, Object>> getPosts(List<Long> postIds) {
		List<Map<String, Object>> result = new ArrayList<>();
		List<Long> misses = new ArrayList<>();
		for (Long id : postIds) {
			try {
				Map<Object, Object> cached = objectRedisTemplate.opsForHash().entries("post:" + id);
				if (cached != null && !cached.isEmpty()) {
					Map<String, Object> post = new HashMap<>();
					cached.forEach((k, v) -> post.put(k.toString(), v));
					result.add(post);
				} else {
					misses.add(id);
				}
			} catch (Exception e) {
				misses.add(id);
			}
		}
		if (!misses.isEmpty()) {
			List<Post> posts = postRepository.findAllById(misses);
			Set<Long> uids = new HashSet<>();
			posts.forEach(p -> {
				if (p.getUserId() != null)
					uids.add(p.getUserId());
			});
			Map<Long, User> userMap = new HashMap<>();
			userRepository.findAllById(uids).forEach(u -> userMap.put(u.getId(), u));
			posts.forEach(p -> result.add(cachePost(p, userMap.get(p.getUserId()))));
		}
		return result;
	}

	private Map<String, Object> cachePost(Post p, User user) {
		String key = "post:" + p.getId();
		Map<String, Object> post = new LinkedHashMap<>();
		post.put("postId", String.valueOf(p.getId()));
		post.put("videoUrl", p.getVideoUrl() != null ? p.getVideoUrl() : "");
		post.put("content", p.getContent() != null ? p.getContent() : "");
		post.put("tags", p.getTags() != null ? p.getTags() : "");
		post.put("userId", p.getUserId() != null ? String.valueOf(p.getUserId()) : "");
		post.put("userName", user != null && user.getName() != null ? user.getName() : "");
		post.put("userAvatar", user != null && user.getAvatar() != null ? user.getAvatar() : "");
		post.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
		post.put("thumbnailUrl", p.getThumbnailUrl() != null ? p.getThumbnailUrl() : "");
		try {
			post.forEach((k, v) -> objectRedisTemplate.opsForHash().put(key, k, v));
			objectRedisTemplate.expire(key, POSTS_TTL);
		} catch (Exception e) {
			log.warn("Cache write fail post {}: {}", p.getId(), e.getMessage());
		}
		return post;
	}

	public void cacheEngagement(Long postId, long likes, long comments) {
		String base = "post:engage:" + postId;
		try {
			stringRedisTemplate.opsForValue().set(base + ":likes", String.valueOf(likes), POST_ENGAGEMENT_TTL);
			stringRedisTemplate.opsForValue().set(base + ":comments", String.valueOf(comments), POST_ENGAGEMENT_TTL);
		} catch (Exception ignored) {
		}
	}

	public Map<String, Long> getCachedEngagement(Long postId) {
		String base = "post:engage:" + postId;
		try {
			String l = stringRedisTemplate.opsForValue().get(base + ":likes");
			String c = stringRedisTemplate.opsForValue().get(base + ":comments");
			if (l != null && c != null)
				return Map.of("likes", Long.parseLong(l), "comments", Long.parseLong(c));
		} catch (Exception ignored) {
		}
		return null;
	}

	public void evict(Long postId) {
		try {
			objectRedisTemplate.delete("post:" + postId);
			stringRedisTemplate.delete("post:engage:" + postId + ":likes");
			stringRedisTemplate.delete("post:engage:" + postId + ":comments");
		} catch (Exception e) {
			log.warn("Cache evict fail post {}", postId);
		}
	}
}
