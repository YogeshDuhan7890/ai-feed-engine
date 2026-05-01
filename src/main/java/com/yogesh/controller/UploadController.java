package com.yogesh.controller;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FanoutService;
import com.yogesh.service.HashtagService;
import com.yogesh.service.CacheInvalidationService;
import com.yogesh.service.ModerationService;
import com.yogesh.util.FileStorageUtil;
import com.yogesh.util.ChapterParserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.yogesh.config.RedisCachePolicy.UPLOAD_STATUS_TTL;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload")
public class UploadController {

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final KafkaTemplate<String, Long> kafkaTemplate;
	private final FanoutService fanoutService;
	private final StringRedisTemplate redisTemplate;
	private final HashtagService hashtagService;
	private final ObjectMapper objectMapper;
	private final ModerationService moderationService;
	private final CacheInvalidationService cacheInvalidationService;

	@Value("${app.kafka.topics.video-processing:video-processing}")
	private String videoProcessingTopic;

	/*
	 * BUG FIX: Memory Leak — ConcurrentHashMap → Redis
	 *
	 * Pehle ka code: private final ConcurrentHashMap<String, UploadStatus>
	 * uploadStatusMap = new ConcurrentHashMap<>(); private final
	 * ConcurrentHashMap<String, SseEmitter> sseEmitters = new
	 * ConcurrentHashMap<>();
	 *
	 * 3 problems the: 1. Server restart pe saara status reset — user ko pata nahi
	 * chalta upload hua ya nahi 2. Multiple server instances (load balancing) mein
	 * kaam nahi karta — ek server pe SSE connect, doosre pe status update → client
	 * ko update nahi milta 3. Memory leak — agar upload beech mein crash ho to
	 * entry map mein hamesha ke liye reh jaati thi, kabhi cleanup nahi hoti
	 *
	 * Fix: Upload status Redis mein store karo with 10 min TTL — - Server restart
	 * safe - Multi-instance safe (Redis shared hota hai) - Auto-cleanup (TTL expire
	 * hone pe Redis khud delete kar deta hai)
	 *
	 * SSE emitters still in-memory hain (yeh theek hai — SSE connection per-server
	 * hoti hai, Redis mein store nahi kar sakte).
	 */
	private static final String STATUS_KEY_PREFIX = "upload:status:";

	// SSE emitters still in-memory (per-connection, cannot be distributed)
	private final ConcurrentHashMap<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

	// ─── Main upload endpoint ─────────────────────────────────────────────────
	@PostMapping(value = "/video", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> uploadVideo(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "content", defaultValue = "") String content,
			@RequestParam(value = "tags", defaultValue = "") String tags,
			@RequestParam(value = "privacy", defaultValue = "public") String privacy,
			@RequestParam(value = "scheduledAt", required = false) String scheduledAt,
			@RequestParam(value = "parentPostId", required = false) Long parentPostId,
			@RequestParam(value = "uploadId", required = false) String uploadId, Authentication auth) {

		if (file == null || file.isEmpty())
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File select karo"));

		String email = auth.getName();
		User user = userRepository.findByEmail(email).orElseThrow();
		if (!content.isBlank()) {
			moderationService.assertAllowed(content, "Caption");
		}
		if (!tags.isBlank()) {
			moderationService.assertAllowed(tags, "Tags");
		}

		if (uploadId == null || uploadId.isBlank())
			uploadId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

		final String uid = uploadId;

		// FIX: Validation pehle, status update baad mein
		String detectedVideoExt;
		try {
			detectedVideoExt = FileStorageUtil.validateAllowedUpload(file, FileStorageUtil.VIDEO_UPLOAD_EXTENSIONS);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}

		updateStatus(uid, "saving", 50, "File save ho rahi hai...");

		try {
			updateStatus(uid, "saving", 60, "Directory create ho rahi hai...");

			// Save file
			String folder = FileStorageUtil.createDateFolder(FileStorageUtil.VIDEO_ORIGINAL);
			String filename = FileStorageUtil.generateFileName(detectedVideoExt);
			Path path = Paths.get(folder + filename);
			file.transferTo(path); // FIX: transferTo() — file directly disk pe likhta hai, getBytes() ki tarah
									// poori file RAM mein load nahi karta

			updateStatus(uid, "processing", 80, "Post save ho rahi hai...");

			// Create post
			Post post = new Post();
			post.setUserId(user.getId());
			post.setContent(content);
			post.setVideoUrl("/" + path.toString().replace("\\", "/"));
			post.setTags(tags.isBlank() ? null : tags);
			if (parentPostId != null && parentPostId > 0) {
				post.setParentPostId(parentPostId);
			}

			// Chapters (optional): parse from caption like "0:00 Intro, 2:30 Main part"
			try {
				var parsed = ChapterParserUtil.parseChaptersFromCaption(content);
				if (parsed != null && parsed.chapters() != null && !parsed.chapters().isEmpty()) {
					post.setChapters(objectMapper.writeValueAsString(parsed.chapters()));
				}
			} catch (Exception ignored) {
			}

			// Scheduling (optional): if scheduledAt is in the future, save as SCHEDULED
			LocalDateTime scheduled = tryParseScheduledAt(scheduledAt);
			if (scheduled != null && scheduled.isAfter(LocalDateTime.now())) {
				post.setScheduledAt(scheduled);
				post.setStatus("SCHEDULED");
			} else {
				post.setStatus("PUBLISHED");
				post.setScheduledAt(null);
			}

			post.setCreatedAt(LocalDateTime.now());
			Post saved = postRepository.save(post);

			updateStatus(uid, "indexing", 90, "Feed update ho raha hai...");

			// Feed cache
			int shard = (int) (user.getId() % 32);
			String feedKey = "feed:shard:" + shard + ":user:" + user.getId();
			redisTemplate.opsForZSet().add(feedKey, String.valueOf(saved.getId()), System.currentTimeMillis());
			cacheInvalidationService.postChanged(saved.getId());

			// Fanout/hashtag/kafka async dispatch — upload response ko block na kare.
			dispatchPostPublishTasksAsync(saved, user);

			updateStatus(uid, "done", 100, "Upload complete! 🎉");

			log.info("Video uploaded: postId={} userId={} size={}KB", saved.getId(), user.getId(),
					file.getSize() / 1024);

			return ResponseEntity.ok(Map.of("success", true, "postId", saved.getId(), "uploadId", uid, "message",
					"Video upload ho gayi!"));

		} catch (Exception e) {
			log.error("Upload failed: {}", e.getMessage(), e);
			updateStatus(uid, "error", 0, "Upload fail ho gaya. Dobara try karo.");
			return ResponseEntity.internalServerError()
					.body(Map.of("success", false, "message", "Upload fail ho gaya. Dobara try karo."));
		}
	}

	private LocalDateTime tryParseScheduledAt(String scheduledAt) {
		if (scheduledAt == null)
			return null;
		String raw = scheduledAt.trim();
		if (raw.isEmpty())
			return null;

		try {
			// For HTML datetime-local: yyyy-MM-ddTHH:mm (seconds may be missing)
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

	// ─── SSE: Real-time progress stream ──────────────────────────────────────
	@GetMapping(value = "/progress/{uploadId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter getProgress(@PathVariable String uploadId) {
		SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout
		sseEmitters.put(uploadId, emitter);

		emitter.onCompletion(() -> sseEmitters.remove(uploadId));
		emitter.onTimeout(() -> {
			sseEmitters.remove(uploadId);
			emitter.complete();
		});

		// Send current status from Redis immediately if exists
		UploadStatus current = getStatusFromRedis(uploadId);
		if (current != null) {
			try {
				emitter.send(current);
			} catch (IOException e) {
				emitter.complete();
			}
		}

		return emitter;
	}

	// ─── Polling: Get upload status ───────────────────────────────────────────
	@GetMapping("/status/{uploadId}")
	public ResponseEntity<UploadStatus> getStatus(@PathVariable String uploadId) {
		UploadStatus status = getStatusFromRedis(uploadId);
		if (status == null)
			status = new UploadStatus(uploadId, "waiting", 0, "Waiting...");
		return ResponseEntity.ok(status);
	}

	// ─── Helper: Update status in Redis + push via SSE ───────────────────────
	private void updateStatus(String uploadId, String stage, int progress, String message) {
		UploadStatus status = new UploadStatus(uploadId, stage, progress, message);

		// Redis mein store karo with TTL
		try {
			String json = objectMapper.writeValueAsString(status);
			redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + uploadId, json, UPLOAD_STATUS_TTL);
		} catch (Exception e) {
			log.warn("Redis status save fail: {}", e.getMessage());
		}

		// SSE via push karo (still in-memory per connection)
		SseEmitter emitter = sseEmitters.get(uploadId);
		if (emitter != null) {
			try {
				emitter.send(status);
				if ("done".equals(stage) || "error".equals(stage)) {
					emitter.complete();
					sseEmitters.remove(uploadId);
				}
			} catch (IOException e) {
				sseEmitters.remove(uploadId);
			}
		}
	}

	// ─── Helper: Redis se status fetch karo ──────────────────────────────────
	private UploadStatus getStatusFromRedis(String uploadId) {
		try {
			String json = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + uploadId);
			if (json == null)
				return null;
			return objectMapper.readValue(json, UploadStatus.class);
		} catch (Exception e) {
			log.warn("Redis status fetch fail: {}", e.getMessage());
			return null;
		}
	}

	// ─── Status record ────────────────────────────────────────────────────────
	public record UploadStatus(String uploadId, String stage, // waiting / saving / processing / indexing / done / error
			int progress, // 0-100
			String message) {
	}

	private void dispatchPostPublishTasksAsync(Post saved, User user) {
		CompletableFuture.runAsync(() -> {
			try {
				fanoutService.pushToFollowers(user.getId(), saved.getId());
			} catch (Exception e) {
				log.warn("Fanout failed: {}", e.getMessage());
			}
		});

		CompletableFuture.runAsync(() -> {
			try {
				hashtagService.indexPost(saved.getId(), saved.getContent());
			} catch (Exception e) {
				log.warn("Hashtag index failed: {}", e.getMessage());
			}
		});

		CompletableFuture.runAsync(() -> {
			try {
				kafkaTemplate.send(videoProcessingTopic, saved.getId()).whenComplete((result, ex) -> {
					if (ex != null) {
						log.warn("Kafka send failed: topic={} postId={} error={}", videoProcessingTopic, saved.getId(),
								ex.getMessage());
					}
				});
			} catch (Exception e) {
				log.warn("Kafka send could not be scheduled: topic={} postId={} error={}", videoProcessingTopic,
						saved.getId(), e.getMessage());
			}
		});
	}
}
