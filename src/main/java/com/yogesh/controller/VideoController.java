package com.yogesh.controller;

import com.yogesh.model.Post;
import com.yogesh.model.User;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.FanoutService;
import com.yogesh.service.CacheInvalidationService;
import com.yogesh.service.ModerationService;
import com.yogesh.util.FileStorageUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/videos")
@Slf4j
public class VideoController {

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final KafkaTemplate<String, Long> kafkaTemplate;

	private final FanoutService fanoutService;
	private final StringRedisTemplate redisTemplate;
	private final ModerationService moderationService;
	private final CacheInvalidationService cacheInvalidationService;

	@Value("${app.kafka.topics.video-processing:video-processing}")
	private String videoProcessingTopic;

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public String uploadVideo(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "content", defaultValue = "") String content, Authentication authentication) {

		try {
			String detectedVideoExt;
			try {
				detectedVideoExt = FileStorageUtil.validateAllowedUpload(file, FileStorageUtil.VIDEO_UPLOAD_EXTENSIONS);
			} catch (IllegalArgumentException e) {
				return "UPLOAD FAILED";
			}

			// 1️⃣ logged user
			String email = authentication.getName();

			User user = userRepository.findByEmail(email).orElseThrow();
			if (!content.isBlank()) {
				moderationService.assertAllowed(content, "Caption");
			}

			// 2️⃣ folder
			String folder = FileStorageUtil.createDateFolder(FileStorageUtil.VIDEO_ORIGINAL);

			// 3️⃣ filename
			String filename = FileStorageUtil.generateFileName(detectedVideoExt);

			Path path = Paths.get(folder + filename);

			// 4️⃣ save file
			file.transferTo(path);

			// 5️⃣ create post
			Post post = new Post();

			post.setUserId(user.getId());
			post.setContent(content);
			post.setVideoUrl("/" + path.toString().replace("\\", "/"));
			post.setCreatedAt(LocalDateTime.now());
			postRepository.save(post);

			Long postId = post.getId();

			/*
			 * 6️⃣ FEED CACHE UPDATE (CREATOR OWN FEED)
			 */

			int shard = (int) (user.getId() % 32);

			String feedKey = "feed:shard:" + shard + ":user:" + user.getId();

			redisTemplate.opsForZSet().add(feedKey, String.valueOf(postId), 1.0);
			cacheInvalidationService.postChanged(postId);

			/*
			 * 7️⃣ FANOUT TO FOLLOWERS
			 */

			fanoutService.pushToFollowers(user.getId(), postId);

			/*
			 * 8️⃣ VIDEO PROCESSING PIPELINE
			 */

			kafkaTemplate.send(videoProcessingTopic, postId).whenComplete((result, ex) -> {
				if (ex != null) {
					log.warn("Kafka send failed: topic={} postId={} error={}", videoProcessingTopic, postId,
							ex.getMessage());
				}
			});

			return "UPLOAD SUCCESS";

		} catch (Exception e) {

			e.printStackTrace();

			return "UPLOAD FAILED";
		}
	}
}
