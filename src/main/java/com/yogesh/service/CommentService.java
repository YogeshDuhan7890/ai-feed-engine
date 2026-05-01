package com.yogesh.service;

import com.yogesh.dto.CommentDTO;
import com.yogesh.model.Comment;
import com.yogesh.model.User;
import com.yogesh.repository.CommentRepository;
import com.yogesh.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final UserRepository userRepository;
	private final StringRedisTemplate redisTemplate;
	private final CacheInvalidationService cacheInvalidationService;

	private static final String LIKE_KEY_PREFIX = "comment:likes:";

	// ─── Get comments with replies ────────────────────────────────────────────
	public List<CommentDTO> getComments(Long postId) {
		// Top-level comments
		List<Comment> topLevel = commentRepository.findByPostIdAndParentIdIsNullOrderByCreatedAtDesc(postId);

		if (topLevel.isEmpty())
			return List.of();

		// Batch load all user IDs
		Set<Long> allUserIds = new HashSet<>();
		topLevel.forEach(c -> {
			if (c.getUserId() != null)
				allUserIds.add(c.getUserId());
		});

		// Load replies for all top-level comments
		Map<Long, List<Comment>> repliesMap = new HashMap<>();
		topLevel.forEach(c -> {
			List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(c.getId());
			if (!replies.isEmpty()) {
				repliesMap.put(c.getId(), replies);
				replies.forEach(r -> {
					if (r.getUserId() != null)
						allUserIds.add(r.getUserId());
				});
			}
		});

		// Batch user fetch
		Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		return topLevel.stream().map(c -> {
			CommentDTO dto = toDTO(c, userMap);
			List<Comment> replies = repliesMap.getOrDefault(c.getId(), List.of());
			dto.setReplies(replies.stream().map(r -> toDTO(r, userMap)).collect(Collectors.toList()));
			return dto;
		}).collect(Collectors.toList());
	}

	// ─── Add comment or reply ─────────────────────────────────────────────────
	public CommentDTO addComment(Long postId, Long userId, String text, Long parentId) {
		Comment c = new Comment();
		c.setPostId(postId);
		c.setUserId(userId);
		c.setText(text.trim());
		c.setParentId(parentId);
		c.setCreatedAt(LocalDateTime.now());

		Comment saved = commentRepository.save(c);

		Map<Long, User> userMap = userRepository.findAllById(List.of(userId)).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		CommentDTO dto = toDTO(saved, userMap);
		dto.setReplies(List.of());
		cacheInvalidationService.commentChanged(postId);
		return dto;
	}

	// Legacy — without parentId
	public CommentDTO addComment(Long postId, Long userId, String text) {
		return addComment(postId, userId, text, null);
	}

	// ─── Delete comment ───────────────────────────────────────────────────────
	public void deleteComment(Long commentId, Long userId) {
		commentRepository.findById(commentId).ifPresent(c -> {
			if (c.getUserId().equals(userId)) {
				// Delete replies too
				commentRepository.findByParentIdOrderByCreatedAtAsc(c.getId())
						.forEach(r -> commentRepository.deleteById(r.getId()));
				commentRepository.delete(c);
				cacheInvalidationService.commentChanged(c.getPostId());
			}
		});
	}

	// ─── Toggle like on comment ───────────────────────────────────────────────
	public int toggleLike(Long commentId, Long userId) {
		String likeKey = LIKE_KEY_PREFIX + commentId;
		String userIdStr = String.valueOf(userId);

		Boolean isLiked = redisTemplate.opsForSet().isMember(likeKey, userIdStr);

		if (Boolean.TRUE.equals(isLiked)) {
			redisTemplate.opsForSet().remove(likeKey, userIdStr);
			commentRepository.decrementLike(commentId);
		} else {
			redisTemplate.opsForSet().add(likeKey, userIdStr);
			commentRepository.incrementLike(commentId);
		}

		Comment updated = commentRepository.findById(commentId).orElseThrow();
		cacheInvalidationService.commentChanged(updated.getPostId());
		return updated.getLikeCount();
	}

	// ─── Count comments ───────────────────────────────────────────────────────
	public long countByPostId(Long postId) {
		return commentRepository.countByPostId(postId);
	}

	// ─── Helper ───────────────────────────────────────────────────────────────
	private CommentDTO toDTO(Comment c, Map<Long, User> userMap) {
		CommentDTO dto = new CommentDTO();
		dto.setId(c.getId());
		dto.setPostId(c.getPostId());
		dto.setUserId(c.getUserId());
		dto.setText(c.getText());
		dto.setParentId(c.getParentId());
		dto.setLikeCount(c.getLikeCount());
		dto.setCreatedAt(c.getCreatedAt());

		User u = c.getUserId() != null ? userMap.get(c.getUserId()) : null;
		if (u != null) {
			dto.setUserName(u.getName());
			dto.setUserAvatar(u.getAvatar());
		}
		return dto;
	}
}
