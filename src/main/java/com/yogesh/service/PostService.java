package com.yogesh.service;

import com.yogesh.model.Post;
import com.yogesh.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PostService {

	private final PostRepository postRepository;
	private final StringRedisTemplate redisTemplate;
	private final HashtagService hashtagService;
	private final CacheInvalidationService cacheInvalidationService;

	public Post upload(Post request) {
		Post post = new Post();
		post.setContent(request.getContent());
		post.setVideoUrl(request.getVideoUrl());
		post.setTags(request.getTags());
		post.setChapters(request.getChapters());
		post.setScheduledAt(request.getScheduledAt());
		post.setStatus(request.getStatus());
		post.setUserId(request.getUserId());
		post.setCreatedAt(LocalDateTime.now());

		// Embedding null check — crash prevent karo
		if (request.getEmbedding() != null && !request.getEmbedding().isBlank()) {
			post.setEmbedding(normalize(request.getEmbedding()));
		}

		Post saved = postRepository.save(post);

		// Redis embedding cache
		if (saved.getEmbedding() != null) {
			try {
				redisTemplate.opsForHash().put("post:embedding",
						String.valueOf(saved.getId()), saved.getEmbedding());
			} catch (Exception e) {
				log.warn("Redis embedding cache failed: {}", e.getMessage());
			}
		}

		// Hashtag indexing
		try {
			hashtagService.indexPost(saved.getId(), saved.getContent());
		} catch (Exception e) {
			log.warn("Hashtag indexing failed for postId={}: {}", saved.getId(), e.getMessage());
		}

		cacheInvalidationService.postChanged(saved.getId());

		log.info("Post uploaded: postId={} userId={}", saved.getId(), saved.getUserId());
		return saved;
	}

	@Transactional(readOnly = true)
	public Post getById(Long postId) {
		return postRepository.findById(postId)
				.orElseThrow(() -> new RuntimeException("Post not found: " + postId));
	}

	public void deletePost(Long postId, Long userId) {
		Post post = postRepository.findById(postId)
				.orElseThrow(() -> new RuntimeException("Post not found"));
		if (!post.getUserId().equals(userId))
			throw new RuntimeException("Not your post");
		postRepository.delete(post);
		redisTemplate.opsForHash().delete("post:embedding", String.valueOf(postId));
		cacheInvalidationService.postChanged(postId);
		log.info("Post deleted: postId={} userId={}", postId, userId);
	}

	private String normalize(String embedding) {
		try {
			String[] parts = embedding.split(",");
			double norm = 0;
			for (String p : parts) {
				double v = Double.parseDouble(p.trim());
				norm += v * v;
			}
			norm = Math.sqrt(norm);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < parts.length; i++) {
				double v = Double.parseDouble(parts[i].trim()) / (norm + 1e-9);
				sb.append(v);
				if (i < parts.length - 1) sb.append(",");
			}
			return sb.toString();
		} catch (Exception e) {
			log.warn("Embedding normalize failed: {}", e.getMessage());
			return embedding;
		}
	}
}
