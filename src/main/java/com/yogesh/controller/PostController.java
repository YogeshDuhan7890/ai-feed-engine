package com.yogesh.controller;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.EngagementRepository;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.ModerationService;
import com.yogesh.service.PostService;
import com.yogesh.util.ChapterParserUtil;
import com.yogesh.util.FileStorageUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final EngagementRepository engagementRepository;
	private final ModerationService moderationService;

	/* POST /api/post/upload — multipart form support */
	@PostMapping(value = "/upload", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE,
			"application/x-www-form-urlencoded" })
	public Post upload(@RequestParam(value = "file", required = false) MultipartFile file,
			@RequestParam(value = "content", defaultValue = "") String content,
			@RequestParam(value = "tags", defaultValue = "") String tags,
			@RequestParam(value = "scheduledAt", required = false) String scheduledAt,
			@RequestParam(value = "parentPostId", required = false) Long parentPostId,
			Authentication auth) {
		User user = userRepository.findByEmail(auth.getName()).orElseThrow();
		if (!content.isBlank()) {
			moderationService.assertAllowed(content, "Caption");
		}
		if (!tags.isBlank()) {
			moderationService.assertAllowed(tags, "Tags");
		}
		Post post = new Post();
		post.setUserId(user.getId());
		post.setContent(content);
		post.setTags(tags.isBlank() ? null : tags);
		if (parentPostId != null && parentPostId > 0) {
			post.setParentPostId(parentPostId);
		}

		// Chapters (optional)
		try {
			var parsed = ChapterParserUtil.parseChaptersFromCaption(content);
			if (parsed != null && parsed.chapters() != null && !parsed.chapters().isEmpty()) {
				post.setChapters(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parsed.chapters()));
			}
		} catch (Exception ignored) {
		}

		// Scheduling (optional)
		LocalDateTime scheduled = tryParseScheduledAt(scheduledAt);
		if (scheduled != null && scheduled.isAfter(LocalDateTime.now())) {
			post.setScheduledAt(scheduled);
			post.setStatus("SCHEDULED");
		}

		if (file != null && !file.isEmpty()) {
			String extension = FileStorageUtil.validateAllowedUpload(file, FileStorageUtil.VIDEO_UPLOAD_EXTENSIONS);
			try {
				// Save file
				String folder = FileStorageUtil.createDateFolder(FileStorageUtil.VIDEO_ORIGINAL);
				String filename = FileStorageUtil.generateFileName(extension);
				java.nio.file.Path path = java.nio.file.Paths.get(folder + filename);
				java.nio.file.Files.createDirectories(path.getParent());
				file.transferTo(path);
				post.setVideoUrl("/" + path.toString().replace("\\", "/"));
			} catch (Exception e) {
				throw new RuntimeException("File save fail: " + e.getMessage());
			}
		}
		return postService.upload(post);
	}

	private LocalDateTime tryParseScheduledAt(String scheduledAt) {
		if (scheduledAt == null) return null;
		String raw = scheduledAt.trim();
		if (raw.isEmpty()) return null;
		try {
			return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch (Exception ignored) {
		}
		try {
			return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
		} catch (Exception ignored) {
		}
		try {
			return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
		} catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * GET /api/post/likes?postIds=1,2,3
	 *
	 * FIX 1: /likes MUST be declared BEFORE /{id} Pehle /{id} pehle tha → Spring
	 * "likes" string ko Long mein convert karne ki koshish karta tha →
	 * MethodArgumentTypeMismatchException: "For input string: likes"
	 *
	 * FIX 2: (Long) cast → ((Number) r).longValue() PostgreSQL pe Hibernate COUNT()
	 * BigInteger return karta hai, Long nahi. Direct (Long) cast →
	 * ClassCastException → 500.
	 */
	@GetMapping("/likes")
	public Map<String, Object> getLikes(@RequestParam List<Long> postIds, Authentication auth) {

		List<Object[]> rows = postRepository.findCountersByPostIds(postIds);
		Map<Long, Long> likeCounts = rows.stream()
				.collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
		Map<Long, Long> viewCounts = rows.stream()
				.collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[2]).longValue()));

		Map<Long, Boolean> likedByMe = new HashMap<>();
		if (auth != null) {
			User user = userRepository.findByEmail(auth.getName()).orElse(null);
			if (user != null) {
				for (Long postId : postIds) {
					likedByMe.put(postId, engagementRepository.existsLikeByUserAndPost(user.getId(), postId));
				}
			}
		}

		Map<String, Object> result = new HashMap<>();
		for (Long postId : postIds) {
			Map<String, Object> data = new HashMap<>();
			data.put("likes", likeCounts.getOrDefault(postId, 0L));
			data.put("likeCount", likeCounts.getOrDefault(postId, 0L));
			data.put("views", viewCounts.getOrDefault(postId, 0L));
			data.put("viewCount", viewCounts.getOrDefault(postId, 0L));
			data.put("likedByMe", likedByMe.getOrDefault(postId, false));
			result.put(String.valueOf(postId), data);
		}
		return result;
	}

	/* GET /api/post/{id} — AFTER /likes, warna "likes" Long mein convert hoga */
	@GetMapping("/{id}")
	public Post getPost(@PathVariable Long id) {
		return postRepository.findById(id).orElseThrow(() -> new RuntimeException("Post not found"));
	}

	/* DELETE /api/post/{id} */
	@DeleteMapping("/{id}")
	public Map<String, String> deletePost(@PathVariable Long id, Authentication auth) {
		User user = userRepository.findByEmail(auth.getName()).orElseThrow();
		postService.deletePost(id, user.getId());
		return Map.of("status", "deleted");
	}
}
