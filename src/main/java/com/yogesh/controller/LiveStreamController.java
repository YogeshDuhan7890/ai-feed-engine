package com.yogesh.controller;

import com.yogesh.model.LiveStream;
import com.yogesh.model.User;
import com.yogesh.repository.LiveStreamRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LiveStreamController {

	private final LiveStreamRepository streamRepo;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate ws;
	private final ModerationService moderationService;

	// ─── REST: Live stream shuru karo ────────────────────────────────────────
	@PostMapping("/api/live/start")
	@ResponseBody
	public Map<String, Object> startStream(@RequestBody Map<String, String> body, Authentication auth) {
		User host = getUser(auth);
		String title = body.getOrDefault("title", host.getName() + " ka live");
		moderationService.assertAllowed(title, "Live title");

		streamRepo.findByHostIdAndStatus(host.getId(), "LIVE").ifPresent(s -> {
			s.setStatus("ENDED");
			s.setEndedAt(LocalDateTime.now());
			streamRepo.save(s);
		});

		LiveStream stream = new LiveStream();
		stream.setHostId(host.getId());
		stream.setTitle(title);
		stream.setStreamKey(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
		stream.setStatus("LIVE");
		streamRepo.save(stream);

		log.info("Live started: host={} streamKey={}", host.getId(), stream.getStreamKey());

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		response.put("streamId", stream.getId());
		response.put("streamKey", stream.getStreamKey());
		response.put("title", stream.getTitle());
		return response;
	}

	// ─── REST: Live stream band karo ─────────────────────────────────────────
	@PostMapping("/api/live/end")
	@ResponseBody
	public Map<String, Object> endStream(Authentication auth) {
		User host = getUser(auth);
		streamRepo.findByHostIdAndStatus(host.getId(), "LIVE").ifPresent(s -> {
			s.setStatus("ENDED");
			s.setEndedAt(LocalDateTime.now());
			streamRepo.save(s);

			Map<String, Object> event = new LinkedHashMap<>();
			event.put("type", "STREAM_ENDED");
			event.put("message", "Stream khatam ho gayi");
			ws.convertAndSend((String) ("/topic/live/" + s.getStreamKey() + "/events"), (Object) event);
			// Also emit on global topic so discovery/active-list clients refresh quickly.
			ws.convertAndSend("/topic/live/events", event);
		});

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		return response;
	}

	// ─── REST: Active streams list ────────────────────────────────────────────
	@GetMapping("/api/live/active")
	@ResponseBody
	public List<Map<String, Object>> getActiveStreams() {
		List<LiveStream> streams = streamRepo.findByStatusOrderByViewerCountDesc("LIVE");
		Set<Long> hostIds = streams.stream().map(LiveStream::getHostId).collect(Collectors.toSet());
		Map<Long, User> userMap = userRepository.findAllById(hostIds).stream()
				.collect(Collectors.toMap(User::getId, u -> u));

		return streams.stream().map(s -> {
			User host = userMap.get(s.getHostId());
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", s.getId());
			m.put("streamKey", s.getStreamKey());
			m.put("title", s.getTitle());
			m.put("hostId", s.getHostId());
			m.put("hostName", host != null ? host.getName() : "User");
			m.put("hostAvatar", host != null ? host.getAvatar() : null);
			m.put("viewerCount", s.getViewerCount());
			m.put("startedAt", s.getStartedAt());
			return m;
		}).collect(Collectors.toList());
	}

	// ─── REST: Stream info by key ─────────────────────────────────────────────
	@GetMapping("/api/live/{streamKey}")
	@ResponseBody
	public Map<String, Object> getStream(@PathVariable String streamKey) {
		LiveStream s = streamRepo.findByStreamKey(streamKey)
				.orElseThrow(() -> new RuntimeException("Stream not found"));
		User host = userRepository.findById(s.getHostId()).orElse(null);

		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", s.getId());
		m.put("streamKey", s.getStreamKey());
		m.put("title", s.getTitle());
		m.put("status", s.getStatus());
		m.put("hostId", s.getHostId());
		m.put("hostName", host != null ? host.getName() : "User");
		m.put("hostAvatar", host != null ? host.getAvatar() : null);
		m.put("viewerCount", s.getViewerCount());
		return m;
	}

	// ─── WebSocket: WebRTC Signaling ──────────────────────────────────────────
	@MessageMapping("/live.offer")
	public void handleOffer(@Payload Map<String, Object> payload) {
		String streamKey = (String) payload.get("streamKey");
		ws.convertAndSend((String) ("/topic/live/" + streamKey + "/offer"), (Object) payload);
	}

	@MessageMapping("/live.answer")
	public void handleAnswer(@Payload Map<String, Object> payload) {
		String streamKey = (String) payload.get("streamKey");
		ws.convertAndSend((String) ("/topic/live/" + streamKey + "/answer"), (Object) payload);
	}

	@MessageMapping("/live.ice")
	public void handleIce(@Payload Map<String, Object> payload) {
		String streamKey = (String) payload.get("streamKey");
		ws.convertAndSend((String) ("/topic/live/" + streamKey + "/ice"), (Object) payload);
	}

	// ─── WebSocket: Viewer join ───────────────────────────────────────────────
	@MessageMapping("/live.join")
	public void handleJoin(@Payload Map<String, Object> payload) {
		String streamKey = (String) payload.get("streamKey");
		String viewerName = (String) payload.getOrDefault("viewerName", "Someone");

		/*
		 * BUG FIX: Race Condition — atomic DB increment Pehle: read → modify → write
		 * (race condition possible tha) Ab: single UPDATE query atomically increment
		 * karti hai
		 */
		streamRepo.incrementViewerCount(streamKey);

		streamRepo.findByStreamKey(streamKey).ifPresent(s -> {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("type", "VIEWER_JOINED");
			event.put("viewerName", viewerName);
			event.put("viewerCount", s.getViewerCount());
			ws.convertAndSend((String) ("/topic/live/" + streamKey + "/events"), (Object) event);
		});
	}

	// ─── WebSocket: Viewer leave ──────────────────────────────────────────────
	@MessageMapping("/live.leave")
	public void handleLeave(@Payload Map<String, Object> payload) {
		String streamKey = (String) payload.get("streamKey");

		/*
		 * BUG FIX: Atomic decrement — GREATEST(..., 0) se negative nahi hoga
		 */
		streamRepo.decrementViewerCount(streamKey);

		streamRepo.findByStreamKey(streamKey).ifPresent(s -> {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("type", "VIEWER_COUNT");
			event.put("viewerCount", s.getViewerCount());
			ws.convertAndSend((String) ("/topic/live/" + streamKey + "/events"), (Object) event);
		});
	}

	// ─── WebSocket: Live chat ─────────────────────────────────────────────────
	@MessageMapping("/live.chat")
	public void handleChat(@Payload Map<String, Object> payload) {
		String streamKey = (String) payload.get("streamKey");
		payload.put("type", "CHAT");
		payload.put("timestamp", System.currentTimeMillis());
		ws.convertAndSend((String) ("/topic/live/" + streamKey + "/chat"), (Object) payload);
	}

	// ─── REST: Send reaction ──────────────────────────────────────────────────
	@PostMapping("/api/live/{streamKey}/react")
	@ResponseBody
	public Map<String, Object> react(@PathVariable String streamKey, @RequestBody Map<String, String> body,
			Authentication auth) {
		String emoji = body.getOrDefault("emoji", "❤️");
		User user = getUser(auth);

		Map<String, Object> event = new LinkedHashMap<>();
		event.put("type", "REACTION");
		event.put("emoji", emoji);
		event.put("senderName", user.getName());
		ws.convertAndSend((String) ("/topic/live/" + streamKey + "/events"), (Object) event);

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		return response;
	}

	// ─── REST: Update title while live ───────────────────────────────────────
	@PostMapping("/api/live/title")
	@ResponseBody
	public Map<String, Object> updateTitle(@RequestBody Map<String, String> body, Authentication auth) {
		User host = getUser(auth);
		streamRepo.findByHostIdAndStatus(host.getId(), "LIVE").ifPresent(s -> {
			String nextTitle = body.getOrDefault("title", s.getTitle());
			moderationService.assertAllowed(nextTitle, "Live title");
			s.setTitle(nextTitle);
			streamRepo.save(s);

			Map<String, Object> event = new LinkedHashMap<>();
			event.put("type", "TITLE_UPDATED");
			event.put("title", s.getTitle());
			ws.convertAndSend((String) ("/topic/live/" + s.getStreamKey() + "/events"), (Object) event);
		});

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", true);
		return response;
	}

	// ─── REST: My active stream ───────────────────────────────────────────────
	@GetMapping("/api/live/my")
	@ResponseBody
	public Map<String, Object> myStream(Authentication auth) {
		User host = getUser(auth);

		/*
		 * BUG FIX: Map.of() cast error Java compiler Map.of() ko Map<String, Object &
		 * Serializable & ...> infer karta hai jo Map<String, Object> mein directly cast
		 * nahi hota. Fix: LinkedHashMap explicitly use karo — no cast needed.
		 */
		Optional<LiveStream> active = streamRepo.findByHostIdAndStatus(host.getId(), "LIVE");

		if (active.isPresent()) {
			LiveStream s = active.get();
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("live", true);
			response.put("streamKey", s.getStreamKey());
			response.put("title", s.getTitle());
			response.put("viewerCount", s.getViewerCount());
			return response;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("live", false);
		return response;
	}

	private User getUser(Authentication auth) {
		return userRepository.findByEmail(auth.getName()).orElseThrow();
	}
}
