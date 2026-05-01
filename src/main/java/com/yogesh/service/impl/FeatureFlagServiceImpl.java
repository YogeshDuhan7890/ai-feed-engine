package com.yogesh.service.impl;

import com.yogesh.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class FeatureFlagServiceImpl implements FeatureFlagService {

	private final StringRedisTemplate redis;

	private String k(String key) {
		return "admin:settings:" + key;
	}

	@Override
	public boolean isEnabled(String key) {
		String v = redis.opsForValue().get(k(key));
		return "true".equalsIgnoreCase(v);
	}

	@Override
	public boolean isEnabledForUser(String key, String uid) {

		// Safe default: flag missing => OFF
		String base = redis.opsForValue().get(k(key));
		if (base == null)
			base = "false";

		if (!"true".equalsIgnoreCase(base))
			return false;

		// rollout key
		String rolloutStr = redis.opsForValue().get(k(key + "_rollout"));

		int pct = 100;
		try {
			// ✅ FIX 2: handle "30%" case
			pct = rolloutStr != null ? Integer.parseInt(rolloutStr.replace("%", "")) : 100;
		} catch (Exception ignored) {
		}

		int bucket = bucket(uid, key);
		return bucket < pct;
	}

	@Override
	public int getInt(String key, int def) {
		String v = redis.opsForValue().get(k(key));
		try {
			return v != null ? Integer.parseInt(v) : def;
		} catch (Exception e) {
			return def;
		}
	}

	@Override
	public String getString(String key, String def) {
		String v = redis.opsForValue().get(k(key));
		return v != null ? v : def;
	}

	// stable hash → 0..99
	private int bucket(String uid, String key) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] d = md.digest((uid + ":" + key).getBytes(StandardCharsets.UTF_8));
			int n = ((d[0] & 0xff) << 8) | (d[1] & 0xff);

			// ✅ FIX 3: safe positive number
			return (n & 0x7fffffff) % 100;

		} catch (Exception e) {
			return Math.abs((uid + key).hashCode()) % 100;
		}
	}
}
