package com.yogesh.controller;

import com.yogesh.dto.UpdateProfileDTO;
import com.yogesh.model.Follow;
import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.BlockRepository;
import com.yogesh.repository.FollowRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FeedService;
import com.yogesh.service.ModerationService;
import com.yogesh.service.ProfileService;
import com.yogesh.util.FileStorageUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;
import java.time.LocalDateTime;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final FollowRepository followRepository;
	private final BlockRepository blockRepository;
	private final ProfileService profileService;
	private final ModerationService moderationService;
	private final FeedService feedService;

	@Value("${upload.path}")
	private String uploadPath;

	/* ── GET MY PROFILE ── */
	@GetMapping("/me")
	public Map<String, Object> getMyProfile(Authentication auth) {
		User user = getUser(auth);
		return buildProfileMap(user, user.getId(), true);
	}

	/* ── GET OTHER USER PROFILE ── */
	@GetMapping("/user/{userId}")
	public Map<String, Object> getUserProfile(@PathVariable Long userId, Authentication auth) {
		User target = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
		Long myId = auth != null ? getUser(auth).getId() : null;
		return buildProfileMap(target, myId, false);
	}

	/* ── UPDATE PROFILE ── */
	@PostMapping("/update")
	public Map<String, String> updateProfile(Authentication auth, @RequestBody UpdateProfileDTO dto) {
		User user = getUser(auth);
		if (dto.getName() != null && !dto.getName().isBlank()) {
			moderationService.assertAllowed(dto.getName(), "Profile name");
		}
		if (dto.getBio() != null && !dto.getBio().isBlank()) {
			moderationService.assertAllowed(dto.getBio(), "Profile bio");
		}
		profileService.updateProfile(user.getId(), dto);

		userRepository.findById(user.getId()).ifPresent(u -> {
			if (dto.getWebsiteUrl() != null)
				u.setWebsiteUrl(dto.getWebsiteUrl().trim());
			if (dto.getUsername() != null) {
				String uname = dto.getUsername().trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
				if (!uname.isBlank())
					u.setUsername(uname);
			}
			userRepository.save(u);
		});

		return Map.of("status", "updated");
	}

	/* ── UPLOAD AVATAR ── */
	@PostMapping("/avatar")
	public String uploadAvatar(Authentication auth, @RequestParam MultipartFile file) throws Exception {
		User user = getUser(auth);

		/*
		 * BUG FIX: getContentType() null return kar sakta hai agar browser content type
		 * set nahi karta — seedha startsWith() call karne se NullPointerException aata
		 * tha. Fix: pehle null check karo.
		 */
		String extension = FileStorageUtil.validateAllowedUpload(file, FileStorageUtil.IMAGE_UPLOAD_EXTENSIONS);
		String filename = FileStorageUtil.generateFileName(extension);
		Path dir = Paths.get(uploadPath);
		Files.createDirectories(dir);
		Files.write(dir.resolve(filename), file.getBytes());

		String avatarUrl = "/uploads/" + filename;
		user.setAvatar(avatarUrl);
		userRepository.save(user);
		return avatarUrl;
	}

	/* ── UPLOAD COVER IMAGE ── */
	@PostMapping("/cover")
	public Map<String, String> uploadCover(Authentication auth, @RequestParam MultipartFile file) throws Exception {
		User user = getUser(auth);

		/*
		 * BUG FIX: Same NPE fix — cover image pe bhi same issue tha.
		 */
		String extension = FileStorageUtil.validateAllowedUpload(file, FileStorageUtil.IMAGE_UPLOAD_EXTENSIONS);
		String filename = FileStorageUtil.generateFileName(extension);
		Path dir = Paths.get(uploadPath);
		Files.createDirectories(dir);
		Files.write(dir.resolve(filename), file.getBytes());

		String coverUrl = "/uploads/" + filename;
		user.setCoverUrl(coverUrl);
		userRepository.save(user);
		return Map.of("coverUrl", coverUrl);
	}

	/* ── MY VIDEOS ── */
	@GetMapping("/user/videos")
	public List<Post> videos(Authentication auth) {
		User me = getUser(auth);
		Set<Long> hiddenIds = feedService.getHiddenPostIds();
		LocalDateTime now = LocalDateTime.now();
		// Pinned posts top pe, baki createdAt desc.
		return postRepository.findByUserIdOrderByIsPinnedDescCreatedAtDesc(me.getId()).stream()
				.filter(post -> post != null && post.getVideoUrl() != null && !post.getVideoUrl().isBlank())
				.filter(post -> !hiddenIds.contains(post.getId()))
				.filter(post -> isPostEligible(post, now))
				.toList();
	}

	/* ── PIN / UNPIN (single pinned per profile) ── */
	@PostMapping("/pin/{postId}")
	@Transactional
	public Map<String, Object> pin(@PathVariable Long postId, Authentication auth) {
		User me = getUser(auth);
		if (postId == null) {
			return Map.of("success", false, "message", "postId required");
		}

		Post post = postRepository.findById(postId).orElse(null);
		if (post == null || post.getUserId() == null || !post.getUserId().equals(me.getId())) {
			return Map.of("success", false, "message", "Aap apni post pin hi kar sakte ho");
		}

		boolean currentlyPinned = post.isPinned();
		if (currentlyPinned) {
			postRepository.clearPinnedByUserId(me.getId());
			return Map.of("success", true, "pinned", false);
		}

		// Ensure single pinned post per user profile.
		postRepository.clearPinnedByUserId(me.getId());
		int updated = postRepository.pinPost(postId, me.getId());
		return Map.of("success", true, "pinned", updated > 0);
	}

	/* ── USER STATS ── */
	@GetMapping("/user/stats/{userId}")
	public Map<String, Object> stats(@PathVariable Long userId, Authentication auth) {
		long followers = followRepository.countByFollowingId(userId);
		long following = followRepository.countByFollowerId(userId);
		long posts = postRepository.countByUserId(userId);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("followers", followers);
		result.put("following", following);
		result.put("posts", posts);

		if (auth != null) {
			Long myId = getUser(auth).getId();
			result.put("isFollowing", "FOLLOWING".equals(getFollowStatus(myId, userId)));
			result.put("isBlocked", blockRepository.existsByBlockerIdAndBlockedId(myId, userId));
			result.put("isMe", myId.equals(userId));
		}
		return result;
	}

	/* ── SUGGESTED USERS ── */
	@GetMapping("/suggested")
	public List<Map<String, Object>> suggested(Authentication auth) {
		Long myId = getUser(auth).getId();
		List<User> users = userRepository.findSuggestedUsers(myId, PageRequest.of(0, 6));
		return users.stream().map(u -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", u.getId());
			m.put("name", u.getName());
			m.put("username", u.getUsername());
			m.put("avatar", u.getAvatar());
			m.put("bio", u.getBio());
			m.put("verified", u.isVerified());
			m.put("followers", followRepository.countByFollowingId(u.getId()));
			return m;
		}).toList();
	}

	/* ── SEARCH USERS ── */
	@GetMapping("/search")
	public List<Map<String, Object>> search(@RequestParam String q) {
		if (q == null || q.trim().length() < 2)
			return List.of();
		return userRepository.searchByNameOrEmail(q.trim(), PageRequest.of(0, 20)).stream().map(u -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", u.getId());
			m.put("name", u.getName());
			m.put("username", u.getUsername());
			m.put("avatar", u.getAvatar());
			m.put("verified", u.isVerified());
			m.put("followers", followRepository.countByFollowingId(u.getId()));
			return m;
		}).toList();
	}

	/* ── BUILD PROFILE MAP ── */
	private Map<String, Object> buildProfileMap(User target, Long viewerId, boolean isMe) {
		long followers = followRepository.countByFollowingId(target.getId());
		long following = followRepository.countByFollowerId(target.getId());
		Set<Long> hiddenIds = feedService.getHiddenPostIds();
		LocalDateTime now = LocalDateTime.now();
		long posts = postRepository.findByUserId(target.getId()).stream()
				.filter(post -> post.getVideoUrl() != null && !post.getVideoUrl().isBlank())
				.filter(post -> !hiddenIds.contains(post.getId()))
				.filter(post -> isMe || isPostEligible(post, now))
				.count();
		String followStatus = getFollowStatus(viewerId, target.getId());
		boolean isFollowing = "FOLLOWING".equals(followStatus);
		boolean isBlocked = viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(viewerId, target.getId());
		boolean blockedByTarget = viewerId != null && blockRepository.existsByBlockerIdAndBlockedId(target.getId(), viewerId);
		boolean contentLocked = (!isMe && target.isPrivateAccount() && !isFollowing) || isBlocked || blockedByTarget;

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", target.getId());
		result.put("name", target.getName());
		result.put("email", isMe ? target.getEmail() : null);
		result.put("username", target.getUsername());
		result.put("bio", target.getBio());
		result.put("avatar", target.getAvatar());
		result.put("coverUrl", target.getCoverUrl());
		result.put("websiteUrl", target.getWebsiteUrl());
		result.put("interests", target.getInterests());
		result.put("verified", target.isVerified());
		result.put("role", target.getRole());
		result.put("followers", followers);
		result.put("following", following);
		result.put("videos", posts);
		result.put("joinedAt", target.getCreatedAt());
		result.put("isMe", isMe);
		result.put("privateAccount", target.isPrivateAccount());
		result.put("contentLocked", contentLocked);
		result.put("blockedByTarget", blockedByTarget);

		if (viewerId != null && !isMe) {
			result.put("isFollowing", isFollowing);
			result.put("followStatus", followStatus);
			result.put("requestPending", "REQUESTED".equals(followStatus));
			result.put("isBlocked", isBlocked);
		}
		return result;
	}

	private boolean isPostEligible(Post p, LocalDateTime now) {
		if (p == null) return false;
		if (p.getScheduledAt() != null && p.getScheduledAt().isAfter(now)) {
			return false;
		}
		String st = p.getStatus();
		if (st != null && "SCHEDULED".equalsIgnoreCase(st)) {
			return p.getScheduledAt() != null && !p.getScheduledAt().isAfter(now);
		}
		return true;
	}

	private User getUser(Authentication auth) {
		return userRepository.findByEmail(auth.getName()).orElseThrow();
	}

	private String getFollowStatus(Long viewerId, Long targetId) {
		if (viewerId == null || viewerId.equals(targetId)) {
			return "NONE";
		}
		return followRepository.findFirstByFollowerIdAndFollowingId(viewerId, targetId)
				.map(follow -> Follow.STATUS_PENDING.equalsIgnoreCase(follow.getStatus()) ? "REQUESTED" : "FOLLOWING")
				.orElse("NONE");
	}
}
