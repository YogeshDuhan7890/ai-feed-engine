package com.yogesh.controller;

import com.yogesh.model.Post;
import com.yogesh.model.Follow;
import com.yogesh.model.User;
import com.yogesh.repository.BlockRepository;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FeedService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final FollowRepository followRepository;
	private final BlockRepository blockRepository;
	private final FeedService feedService;

	/*
	 * ======================== GET ANY USER'S PUBLIC PROFILE GET
	 * /api/user/{userId}/profile ========================
	 */
	@GetMapping("/{userId}/profile")
	public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable Long userId,
			Authentication authentication) {

		User target = userRepository.findById(userId).orElse(null);
		if (target == null)
			return ResponseEntity.notFound().build();

		// Check karo ki current user follow karta hai ya nahi
		Long viewerId = authentication != null ? getViewerId(authentication) : null;
		String followStatus = getFollowStatus(viewerId, userId);
		boolean isFollowing = "FOLLOWING".equals(followStatus);
		boolean requestPending = "REQUESTED".equals(followStatus);
		boolean isBlocked = viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(viewerId, userId);
		boolean blockedByTarget = viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(userId, viewerId);
		Set<Long> hiddenIds = feedService.getHiddenPostIds();

		long followerCount = followRepository.countByFollowingId(userId);
		long followingCount = followRepository.countByFollowerId(userId);
		long videoCount = postRepository.findByUserId(userId).stream()
				.filter(post -> post.getVideoUrl() != null && !post.getVideoUrl().isBlank())
				.filter(post -> !hiddenIds.contains(post.getId()))
				.count();
		boolean isMe = viewerId != null && viewerId.equals(userId);
		boolean contentLocked = (target.isPrivateAccount() && !isFollowing && !isMe) || isBlocked || blockedByTarget;

		Map<String, Object> profile = new HashMap<>();
		profile.put("id", target.getId());
		profile.put("name", target.getName());
		profile.put("bio", target.getBio());
		profile.put("avatar", target.getAvatar());
		profile.put("followers", followerCount);
		profile.put("following", followingCount);
		profile.put("videos", videoCount);
		profile.put("isFollowing", isFollowing);
		profile.put("followStatus", followStatus);
		profile.put("requestPending", requestPending);
		profile.put("isBlocked", isBlocked);
		profile.put("blockedByTarget", blockedByTarget);
		profile.put("privateAccount", target.isPrivateAccount());
		profile.put("contentLocked", contentLocked);

		return ResponseEntity.ok(profile);
	}

	/*
	 * ======================== GET ANY USER'S VIDEOS GET /api/user/{userId}/videos
	 * ========================
	 */
	@GetMapping("/{userId}/videos")
	public ResponseEntity<List<Map<String, Object>>> getUserVideos(@PathVariable Long userId,
			Authentication authentication) {

		User target = userRepository.findById(userId).orElse(null);
		if (target == null)
			return ResponseEntity.notFound().build();

		Long viewerId = authentication != null ? getViewerId(authentication) : null;
		boolean isMe = viewerId != null && viewerId.equals(userId);
		boolean isFollowing = "FOLLOWING".equals(getFollowStatus(viewerId, userId));
		boolean isBlocked = viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(viewerId, userId);
		boolean blockedByTarget = viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(userId, viewerId);
		if ((target.isPrivateAccount() && !isMe && !isFollowing) || isBlocked || blockedByTarget) {
			return ResponseEntity.ok(List.of());
		}

		List<Post> posts = postRepository.findByUserIdOrderByIsPinnedDescCreatedAtDesc(userId);
		Set<Long> hiddenIds = feedService.getHiddenPostIds();
		LocalDateTime now = LocalDateTime.now();

		List<Map<String, Object>> result = new ArrayList<>();
		for (Post p : posts) {
			if (p == null || p.getVideoUrl() == null || p.getVideoUrl().isBlank() || hiddenIds.contains(p.getId()))
				continue;
			if (p.getScheduledAt() != null && p.getScheduledAt().isAfter(now)) {
				continue;
			}
			Map<String, Object> item = new HashMap<>();
			item.put("postId", p.getId());
			item.put("videoUrl", p.getVideoUrl());
			item.put("content", p.getContent() != null ? p.getContent() : "");
			item.put("isPinned", p.isPinned());
			result.add(item);
		}

		return ResponseEntity.ok(result);
	}

	private Long getViewerId(Authentication authentication) {
		return userRepository.findByEmail(authentication.getName()).map(User::getId).orElse(null);
	}

	private String getFollowStatus(Long viewerId, Long userId) {
		if (viewerId == null || viewerId.equals(userId)) {
			return "NONE";
		}
		return followRepository.findFirstByFollowerIdAndFollowingId(viewerId, userId)
				.map(follow -> Follow.STATUS_PENDING.equalsIgnoreCase(follow.getStatus()) ? "REQUESTED" : "FOLLOWING")
				.orElse("NONE");
	}
}
