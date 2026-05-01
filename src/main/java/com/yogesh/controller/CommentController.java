package com.yogesh.controller;

import com.yogesh.dto.CommentDTO;
import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.CommentService;
import com.yogesh.service.ModerationService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;
	private final UserRepository userRepository;
	private final ModerationService moderationService;

	/* GET /api/comments/{postId} */
	@GetMapping("/{postId}")
	public List<CommentDTO> getComments(@PathVariable Long postId) {
		return commentService.getComments(postId);
	}

	/* POST /api/comments/{postId} — comment add */
	@PostMapping("/{postId}")
	public CommentDTO addComment(@PathVariable Long postId, @RequestBody Map<String, String> body,
			Authentication auth) {
		User user = userRepository.findByEmail(auth.getName()).orElseThrow();
		String text = body.get("text");
		String parentId = body.get("parentId"); // replies ke liye

		if (text == null || text.isBlank())
			throw new IllegalArgumentException("Comment text empty nahi ho sakta");
		moderationService.assertAllowed(text, "Comment");

		Long parentCommentId = parentId != null && !parentId.isBlank() ? Long.parseLong(parentId) : null;

		return commentService.addComment(postId, user.getId(), text, parentCommentId);
	}

	/* DELETE /api/comments/{commentId} */
	@DeleteMapping("/{commentId}")
	public Map<String, String> deleteComment(@PathVariable Long commentId, Authentication auth) {
		User user = userRepository.findByEmail(auth.getName()).orElseThrow();
		commentService.deleteComment(commentId, user.getId());
		return Map.of("status", "deleted");
	}

	/* POST /api/comments/{commentId}/like — comment like karo */
	@PostMapping("/{commentId}/like")
	public Map<String, Object> likeComment(@PathVariable Long commentId, Authentication auth) {
		User user = userRepository.findByEmail(auth.getName()).orElseThrow();
		int likes = commentService.toggleLike(commentId, user.getId());
		return Map.of("likes", likes);
	}

	/* GET /api/comments/{postId}/count */
	@GetMapping("/{postId}/count")
	public Map<String, Long> getCount(@PathVariable Long postId) {
		return Map.of("count", commentService.countByPostId(postId));
	}
}
