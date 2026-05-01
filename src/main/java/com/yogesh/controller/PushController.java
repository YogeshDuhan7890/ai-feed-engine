// ===== PushController.java =====
package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

	private final PushNotificationService pushService;
	private final UserRepository userRepository;

	/** GET /api/push/vapid-key — frontend ko public key chahiye */
	@GetMapping("/vapid-key")
	public Map<String, String> vapidKey() {
		return Map.of("publicKey", pushService.getVapidPublicKey());
	}

	/** POST /api/push/subscribe */
	@PostMapping("/subscribe")
	public Map<String, String> subscribe(@RequestBody Map<String, Object> body, Authentication auth) {
		User me = userRepository.findByEmail(auth.getName()).orElseThrow();
		String endpoint = (String) body.get("endpoint");
		Map<String, String> keys = (Map<String, String>) body.get("keys");
		pushService.subscribe(me.getId(), endpoint, keys.get("p256dh"), keys.get("auth"));
		return Map.of("status", "subscribed");
	}

	/** POST /api/push/unsubscribe */
	@PostMapping("/unsubscribe")
	public Map<String, String> unsubscribe(@RequestBody Map<String, String> body) {
		pushService.unsubscribe(body.get("endpoint"));
		return Map.of("status", "unsubscribed");
	}

	/**
	 * GET /api/push/generate-keys — VAPID keys generate karo (admin only, ek baar)
	 */
	@GetMapping("/generate-keys")
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, String> generateKeys() {
		try {
			Map<String, String> generated = com.yogesh.util.VapidKeyGenerator.generate();
			return pushService.saveRuntimeVapidKeys(generated.get("publicKey"), generated.get("privateKey"),
					generated.getOrDefault("subject", "mailto:admin@aifeed.com"));
		} catch (Exception e) {
			return Map.of("error", e.getMessage());
		}
	}

	/** GET /api/push/status — push config status */
	@GetMapping("/status")
	public Map<String, Object> status() {
		return Map.of("configured", pushService.isVapidConfigured(), "totalSubscriptions",
				pushService.getTotalSubscriptions(), "activeSubscribers", pushService.getActiveSubscriberCount(),
				"publicKey", pushService.getVapidPublicKey() != null ? "set" : "not set");
	}
}

// ===== HashtagController.java =====
