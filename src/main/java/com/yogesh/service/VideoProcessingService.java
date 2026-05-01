package com.yogesh.service;

import com.yogesh.model.Post;
import com.yogesh.model.VideoAudioTrack;
import com.yogesh.repository.PostRepository;
import com.yogesh.repository.VideoAudioRepository;
import com.yogesh.util.FFmpegUtil;
import com.yogesh.util.FileStorageUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingService {

	private final PostRepository postRepository;
	private final VideoAudioRepository videoAudioRepository;
	private final OpenAiService openAiService;

	@KafkaListener(topics = "${app.kafka.topics.video-processing:video-processing}", groupId = "video-group", containerFactory = "kafkaListenerContainerFactory")
	public void processVideo(Long postId) {
		try {
			log.info("Processing video for postId: {}", postId);

			Post post = postRepository.findById(postId)
					.orElseThrow(() -> new RuntimeException("Post not found: " + postId));

			String videoPath = post.getVideoUrl();
			if (videoPath == null || videoPath.isBlank()) {
				throw new IllegalStateException("Video path is empty for postId: " + postId);
			}

			String fsPath = videoPath.startsWith("/") ? videoPath.substring(1) : videoPath;

			// Step 1: Thumbnail
			generateThumbnail(fsPath, post);

			// Step 2: HLS
			convertToHLS(post, fsPath);

			// Step 3: Free AI processing
			processWithFreeAI(postId, fsPath, post);

			log.info("Video processing complete for postId: {}", postId);

		} catch (Exception e) {
			log.error("Video processing failed for postId: {}", postId, e);
			throw new IllegalStateException("Video processing failed for postId: " + postId, e);
		}
	}

	// ── Free AI Pipeline ──────────────────────────────────────────
	private void processWithFreeAI(Long postId, String fsPath, Post post) {
		try {
			// Step A: Audio extract
			String extractedAudio = extractAudio(fsPath);
			if (extractedAudio == null)
				return;

			// Step B: Speech to text (Whisper local — agar available ho)
			String transcript = openAiService.speechToText(extractedAudio);

			// Step C: Auto caption agar post mein nahi hai
			if (!transcript.isBlank()) {
				if (post.getContent() == null || post.getContent().isBlank()) {
					Map<String, String> captionData = openAiService.generateCaption(transcript);
					post.setContent(captionData.getOrDefault("caption", ""));
					post.setTags(captionData.getOrDefault("tags", ""));
					postRepository.save(post);
					log.info("Auto caption saved for postId={}", postId);
				}

				// Step D: Hindi translation (MyMemory — free)
				String detectedLang = openAiService.detectLanguage(transcript);
				if ("en".equals(detectedLang)) {
					String hindi = openAiService.translate(transcript, "hi");
					if (!hindi.isBlank() && !hindi.equals(transcript)) {
						saveTrack(postId, "hi", hindi); // text store karo (frontend TTS use karega)
					}
				} else {
					String english = openAiService.translate(transcript, "en");
					if (!english.isBlank()) {
						saveTrack(postId, "en", english);
					}
				}
			}

		} catch (Exception e) {
			log.error("Free AI processing error for postId={}: {}", postId, e.getMessage());
		}
	}

	// ── Audio extract ─────────────────────────────────────────────
	private String extractAudio(String videoPath) {
		try {
			String folder = FileStorageUtil.createDateFolder(FileStorageUtil.AUDIO_EXTRACTED);
			String output = folder + FileStorageUtil.generateFileName("mp3");
			FFmpegUtil.extractAudio(videoPath, output);
			return output;
		} catch (Exception e) {
			log.error("Audio extract failed: {}", e.getMessage());
			return null;
		}
	}

	// ── Thumbnail ─────────────────────────────────────────────────
	private void generateThumbnail(String videoPath, Post post) throws Exception {
		String folder = FileStorageUtil.createDateFolder(FileStorageUtil.THUMBNAILS);
		String thumbnail = folder + FileStorageUtil.generateFileName("jpg");
		FFmpegUtil.generateThumbnail(videoPath, thumbnail);
		String publicUrl = "/" + thumbnail.replace("\\", "/");
		post.setThumbnailUrl(publicUrl);
		postRepository.save(post);
		log.info("Thumbnail generated: {}", thumbnail);
	}

	// ── HLS ───────────────────────────────────────────────────────
	private void convertToHLS(Post post, String videoPath) throws Exception {
		String folder = FileStorageUtil.HLS + post.getId() + "/";
		Files.createDirectories(Paths.get(folder));
		FFmpegUtil.convertToMultiBitrateHLS(videoPath, folder);
		String master = folder + "master.m3u8";
		String publicUrl = "/" + master.replace("\\", "/");
		post.setVideoUrl(publicUrl);
		postRepository.save(post);
		log.info("HLS generated (multi-bitrate): {}", master);
	}

	// ── Save track (text ke liye — frontend browser TTS use karega) ──
	private void saveTrack(Long postId, String lang, String textOrUrl) {
		VideoAudioTrack track = new VideoAudioTrack();
		track.setPostId(postId);
		track.setLanguage(lang);
		track.setAudioUrl(textOrUrl); // translated text store ho raha hai
		videoAudioRepository.save(track);
	}
}
