package com.yogesh.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginTrackingService {

	private final StringRedisTemplate redis;
	private final ObjectMapper om;
	private final DeviceLocationService deviceLocationService;

	public void trackLogin(HttpServletRequest request, String email) {

		try {
			String sessionId = request.getSession().getId();
			String ip = getClientIP(request);
			String ua = request.getHeader("User-Agent");

			if (ua == null)
				ua = "Unknown";

			// ✅ Device + Location
			Map<String, Object> device = deviceLocationService.extractDevice(ua);
			Map<String, Object> location = deviceLocationService.getLocation(ip);

			// ── Last known device/location
			String deviceKey = "user:last:device:" + email;
			String locationKey = "user:last:location:" + email;

			String lastDevice = redis.opsForValue().get(deviceKey);
			String lastLocation = redis.opsForValue().get(locationKey);

			String currentDevice = device.getOrDefault("browser", "Unknown") + "_"
					+ device.getOrDefault("os", "Unknown");

			String currentLocation = location.getOrDefault("city", "Unknown") + "_"
					+ location.getOrDefault("country", "Unknown");

			boolean newDevice = lastDevice != null && !lastDevice.equals(currentDevice);
			boolean newLocation = lastLocation != null && !lastLocation.equals(currentLocation);

			redis.opsForValue().set(deviceKey, currentDevice, Duration.ofDays(7));
			redis.opsForValue().set(locationKey, currentLocation, Duration.ofDays(7));

			// ── Session Data
			Map<String, Object> session = new HashMap<>();
			session.put("sessionId", sessionId);
			session.put("email", email);
			session.put("ip", ip);
			session.put("device", device);
			session.put("location", location);
			session.put("loginTime", LocalDateTime.now());
			session.put("newDevice", newDevice);
			session.put("newLocation", newLocation);

			String json = om.writeValueAsString(session);

			// ✅ STORE SESSION
			redis.opsForValue().set("session:data:" + sessionId, json, Duration.ofHours(6));

			// ✅ USER SESSION LIST
			String userSessionKey = "user:sessions:" + email;
			redis.opsForSet().add(userSessionKey, sessionId);
			redis.expire(userSessionKey, Duration.ofHours(6));

			// ── Alerts
			if (newDevice || newLocation) {

				Map<String, Object> alert = new HashMap<>();
				alert.put("email", email);
				alert.put("time", LocalDateTime.now());
				alert.put("reason", newDevice ? "NEW_DEVICE" : "NEW_LOCATION");
				alert.put("ip", ip);
				alert.put("location", location);

				redis.opsForList().leftPush("user:alerts:" + email, om.writeValueAsString(alert));

				redis.opsForList().trim("user:alerts:" + email, 0, 49);
			}

		} catch (Exception e) {
			e.printStackTrace(); // ❗ debugging visible now
		}
	}

	private String getClientIP(HttpServletRequest request) {

		String ip = request.getHeader("X-Forwarded-For");

		if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
			return ip.split(",")[0].trim();
		}

		ip = request.getHeader("X-Real-IP");
		if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
			return ip;
		}

		return request.getRemoteAddr();
	}
}