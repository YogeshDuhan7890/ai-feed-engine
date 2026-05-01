package com.yogesh.websocket;

import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceListener {

	private final SimpMessagingTemplate messagingTemplate;
	private final StringRedisTemplate redisTemplate;
	private final UserRepository userRepository;

	@EventListener
	public void handleConnect(SessionConnectedEvent event) {
		try {
			StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
			String email = event.getUser() != null ? event.getUser().getName() : null;
			String sessionId = accessor.getSessionId();
			if (email == null || email.isBlank() || "unknown".equals(email))
				return;

			Long userId = userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
			if (userId == null)
				return;

			redisTemplate.opsForValue().set("online:user:" + userId, "1", Duration.ofMinutes(5));
			if (sessionId != null) {
				redisTemplate.opsForValue().set("session:" + sessionId, String.valueOf(userId), Duration.ofMinutes(10));
			}

			messagingTemplate.convertAndSend("/topic/presence",
					Map.of("userId", userId, "status", "online", "timestamp", System.currentTimeMillis()));
			log.debug("User connected: {}", userId);
		} catch (Exception e) {
			log.warn("Connect event error: {}", e.getMessage());
		}
	}

	@EventListener
	public void handleDisconnect(SessionDisconnectEvent event) {
		try {
			StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
			String email = event.getUser() != null ? event.getUser().getName() : null;
			String sessionId = accessor.getSessionId();
			Long userId = null;

			if (email != null && !email.isBlank()) {
				userId = userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
			}
			if (userId == null && sessionId != null) {
				String stored = redisTemplate.opsForValue().get("session:" + sessionId);
				if (stored != null && !stored.isBlank()) {
					userId = Long.parseLong(stored);
				}
			}
			if (userId == null)
				return;

			redisTemplate.delete("online:user:" + userId);
			if (sessionId != null) {
				redisTemplate.delete("session:" + sessionId);
			}
			redisTemplate.opsForValue().set("last_seen:user:" + userId, String.valueOf(System.currentTimeMillis()),
					Duration.ofDays(7));

			messagingTemplate.convertAndSend("/topic/presence",
					Map.of("userId", userId, "status", "offline", "lastSeen", System.currentTimeMillis()));
			log.debug("User disconnected: {}", userId);
		} catch (Exception e) {
			log.warn("Disconnect event error: {}", e.getMessage());
		}
	}
}
