package com.yogesh.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotificationBroadcaster {

	private final SimpMessagingTemplate messagingTemplate;
	private final StringRedisTemplate redisTemplate;

	@Scheduled(fixedDelay = 30000)
	public void broadcastAdminAlerts() {
		try {
			List<Map<String, Object>> alerts = buildAlerts();
			if (!alerts.isEmpty()) {
				Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("alerts", alerts);
				payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
				payload.put("count", alerts.size());

				// BUG FIX: convertAndSend(String, Object) — explicitly String destination
				// Spring 6 mein convertAndSend ambiguous ho gaya tha:
				// convertAndSend(D dest, Object) vs convertAndSend(Object, Map headers)
				// Fix: messagingTemplate.convertAndSend(String destination, Object payload)
				messagingTemplate.convertAndSend((String) "/topic/admin/alerts", (Object) payload);
			}
		} catch (Exception e) {
			log.warn("Admin alert broadcast fail: {}", e.getMessage());
		}
	}

	private List<Map<String, Object>> buildAlerts() {
		List<Map<String, Object>> alerts = new ArrayList<>();
		try {
			List<String> reports = redisTemplate.opsForList().range("admin:reports:pending", 0, -1);
			long pendingReports = reports != null ? reports.size() : 0;
			if (pendingReports > 0) {
				alerts.add(alert("REPORT", pendingReports + " pending reports hain",
						pendingReports > 5 ? "HIGH" : "MEDIUM", pendingReports));
			}

			String lastScan = redisTemplate.opsForValue().get("admin:moderation:lastScanCount");
			if (lastScan != null && Long.parseLong(lastScan) > 0) {
				alerts.add(
						alert("MODERATION", lastScan + " posts flagged last scan", "MEDIUM", Long.parseLong(lastScan)));
			}

			Runtime rt = Runtime.getRuntime();
			long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
			long maxMB = rt.maxMemory() / (1024 * 1024);
			int heapPct = maxMB > 0 ? (int) (usedMB * 100 / maxMB) : 0;
			if (heapPct >= 85) {
				alerts.add(alert("SYSTEM", "Heap memory critical: " + heapPct + "%", "HIGH", (long) heapPct));
			} else if (heapPct >= 70) {
				alerts.add(alert("SYSTEM", "Heap memory high: " + heapPct + "%", "MEDIUM", (long) heapPct));
			}

			List<String> recentAudit = redisTemplate.opsForList().range("admin:audit:log", 0, 0);
			if (recentAudit != null && !recentAudit.isEmpty()) {
				try {
					String entry = recentAudit.get(0);
					int ai = entry.indexOf("\"action\":\"") + 10;
					int bi = entry.indexOf("\"", ai);
					if (ai > 10 && bi > ai) {
						String action = entry.substring(ai, bi);
						alerts.add(alert("AUDIT", "Recent action: " + action, "INFO", 1L));
					}
				} catch (Exception ignored) {
				}
			}
		} catch (Exception e) {
			log.warn("Build alerts fail: {}", e.getMessage());
		}
		return alerts;
	}

	private Map<String, Object> alert(String type, String message, String severity, Long count) {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put("type", type);
		a.put("message", message);
		a.put("severity", severity);
		a.put("count", count);
		return a;
	}
}