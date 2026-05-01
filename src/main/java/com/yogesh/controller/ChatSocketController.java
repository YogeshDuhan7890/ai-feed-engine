package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.DirectMessageService;
import com.yogesh.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

	private final DirectMessageService dmService;
	private final SimpMessagingTemplate messagingTemplate;
	private final StringRedisTemplate redisTemplate;
	private final UserRepository userRepository;
	private final ModerationService moderationService;

	@MessageMapping("/chat.send")
	public void sendMessage(@Payload Map<String, Object> payload, Principal principal) {
		try {
			User me = requireUser(principal);
			Long receiverId = Long.valueOf(payload.get("receiverId").toString());
			String text = payload.getOrDefault("text", "").toString();
			moderationService.assertAllowed(text, "Message");

			Map<String, Object> msg = dmService.send(me.getId(), receiverId, text);
			msg.put("type", "MESSAGE");

			User receiver = userRepository.findById(receiverId).orElseThrow();
			messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/messages", msg);
			messagingTemplate.convertAndSendToUser(me.getEmail(), "/queue/messages", msg);
		} catch (Exception e) {
			log.warn("WebSocket send error: {}", e.getMessage());
			if (principal != null && principal.getName() != null) {
				messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/messages",
						Map.of("type", "ERROR", "message", e.getMessage()));
			}
		}
	}

	@MessageMapping("/chat.typing")
	public void typing(@Payload Map<String, Object> payload, Principal principal) {
		try {
			User me = requireUser(principal);
			Long receiverId = Long.valueOf(payload.get("receiverId").toString());
			boolean isTyping = Boolean.parseBoolean(payload.getOrDefault("typing", "false").toString());

			Map<String, Object> typingMsg = new HashMap<>();
			typingMsg.put("senderId", me.getId());
			typingMsg.put("typing", isTyping);
			typingMsg.put("type", "TYPING");

			userRepository.findById(receiverId).ifPresent(receiver -> messagingTemplate.convertAndSendToUser(
					receiver.getEmail(), "/queue/typing", typingMsg));
		} catch (Exception e) {
			log.warn("Typing indicator error: {}", e.getMessage());
		}
	}

	@MessageMapping("/chat.heartbeat")
	public void heartbeat(Principal principal) {
		try {
			User me = requireUser(principal);
			redisTemplate.opsForValue().set("online:user:" + me.getId(), "1", Duration.ofMinutes(5));
		} catch (Exception ignored) {
		}
	}

	private User requireUser(Principal principal) {
		if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
			throw new IllegalStateException("Unauthenticated websocket session");
		}
		return userRepository.findByEmail(principal.getName()).orElseThrow();
	}

	@RestController
	@RequestMapping("/api/presence")
	@RequiredArgsConstructor
	static class PresenceRestController {

		private final StringRedisTemplate redisTemplate;

		@GetMapping("/online/{userId}")
		public Map<String, Object> isOnline(@PathVariable Long userId) {
			boolean online = Boolean.TRUE.equals(redisTemplate.hasKey("online:user:" + userId));
			String lastSeenStr = redisTemplate.opsForValue().get("last_seen:user:" + userId);
			return Map.of("online", online, "userId", userId, "lastSeen",
					lastSeenStr != null ? Long.parseLong(lastSeenStr) : 0);
		}

		@PostMapping("/online/batch")
		public Map<String, Object> batchOnline(@RequestBody Map<String, Object> body) {
			try {
				@SuppressWarnings("unchecked")
				List<Object> userIds = (List<Object>) body.get("userIds");
				Map<String, Boolean> result = new HashMap<>();
				if (userIds != null) {
					for (Object userId : userIds) {
						String key = String.valueOf(userId);
						result.put(key, Boolean.TRUE.equals(redisTemplate.hasKey("online:user:" + key)));
					}
				}
				return Map.of("online", result);
			} catch (Exception e) {
				return Map.of("online", Map.of());
			}
		}
	}
}
