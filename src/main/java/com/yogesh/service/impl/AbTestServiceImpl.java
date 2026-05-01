package com.yogesh.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogesh.admin.service.AbTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AbTestServiceImpl implements AbTestService {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String assignVariant(String testId, String userId) {

		String userKey = "admin:ab:user:" + testId + ":" + userId;

		// ✅ 1. Sticky assignment
		String existing = redisTemplate.opsForValue().get(userKey);
		if (existing != null) {
			return existing;
		}

		try {
			// ✅ 2. Check test enabled
			String enabled = redisTemplate.opsForValue().get("admin:ab:test:" + testId + ":enabled");

			if (!"true".equalsIgnoreCase(enabled)) {
				return "A";
			}

			// ✅ 3. Get test config
			String data = redisTemplate.opsForValue().get("admin:ab:test:" + testId);

			if (data == null)
				return "A";

			Map<String, Object> map = objectMapper.readValue(data, Map.class);

			// ✅ FIX: safe casting
			int split = ((Number) map.get("split")).intValue();

			// ✅ FIX: deterministic hashing (NO random)
			int bucket = Math.abs((userId + testId).hashCode()) % 100;

			String variant = bucket < split ? "A" : "B";

			// ✅ FIX: TTL added
			redisTemplate.opsForValue().set(userKey, variant, Duration.ofDays(30));

			// ✅ increment counter
			String counterKey = "admin:ab:" + testId + ":" + variant.toLowerCase();
			redisTemplate.opsForValue().increment(counterKey);

			return variant;

		} catch (Exception e) {
			e.printStackTrace();
			return "A";
		}
	}

	@Override
	public void trackExposure(String testId, String userId, String variant) {
		if (testId == null || testId.isBlank() || userId == null || userId.isBlank()) {
			return;
		}
		String v = normalizeVariant(variant);
		String dedupeKey = "admin:ab:exp:dedupe:" + testId + ":" + userId;
		try {
			Boolean first = redisTemplate.opsForValue().setIfAbsent(dedupeKey, v, 30, TimeUnit.DAYS);
			if (!Boolean.TRUE.equals(first)) {
				return;
			}
			redisTemplate.opsForValue().increment("admin:ab:" + testId + ":" + v.toLowerCase() + ":exposures");
		} catch (Exception ignored) {
		}
	}

	@Override
	public void trackConversion(String testId, String userId, String variant) {
		if (testId == null || testId.isBlank() || userId == null || userId.isBlank()) {
			return;
		}
		String v = normalizeVariant(variant);
		String dedupeKey = "admin:ab:conv:dedupe:" + testId + ":" + userId;
		try {
			Boolean first = redisTemplate.opsForValue().setIfAbsent(dedupeKey, v, 30, TimeUnit.DAYS);
			if (!Boolean.TRUE.equals(first)) {
				return;
			}
			redisTemplate.opsForValue().increment("admin:ab:" + testId + ":" + v.toLowerCase() + ":conversions");
		} catch (Exception ignored) {
		}
	}

	@Override
	public Map<String, Object> getResults(String testId) {
		long aExp = getLong("admin:ab:" + testId + ":a:exposures");
		long bExp = getLong("admin:ab:" + testId + ":b:exposures");
		long aConv = getLong("admin:ab:" + testId + ":a:conversions");
		long bConv = getLong("admin:ab:" + testId + ":b:conversions");

		double aCr = aExp > 0 ? ((double) aConv / aExp) : 0.0;
		double bCr = bExp > 0 ? ((double) bConv / bExp) : 0.0;
		double liftPct = aCr > 0 ? ((bCr - aCr) / aCr) * 100.0 : (bCr > 0 ? 100.0 : 0.0);

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("testId", testId);
		out.put("aExposures", aExp);
		out.put("bExposures", bExp);
		out.put("aConversions", aConv);
		out.put("bConversions", bConv);
		out.put("aConversionRate", round4(aCr));
		out.put("bConversionRate", round4(bCr));
		out.put("liftPctBOverA", round2(liftPct));
		out.put("totalExposures", aExp + bExp);
		out.put("totalConversions", aConv + bConv);
		return out;
	}

	@Override
	public Map<String, Object> chooseWinner(String testId, double minLiftPct, long minSamplePerVariant) {
		Map<String, Object> results = getResults(testId);
		long aExp = ((Number) results.get("aExposures")).longValue();
		long bExp = ((Number) results.get("bExposures")).longValue();
		double lift = ((Number) results.get("liftPctBOverA")).doubleValue();
		double aCr = ((Number) results.get("aConversionRate")).doubleValue();
		double bCr = ((Number) results.get("bConversionRate")).doubleValue();

		String winner = "NONE";
		String reason = "Insufficient data";
		if (aExp >= minSamplePerVariant && bExp >= minSamplePerVariant) {
			if (Math.abs(lift) >= minLiftPct) {
				winner = bCr >= aCr ? "B" : "A";
				reason = "Winner selected by conversion lift";
			} else {
				reason = "Lift below threshold";
			}
		}

		Map<String, Object> out = new LinkedHashMap<>(results);
		out.put("winner", winner);
		out.put("decisionReason", reason);
		out.put("minLiftPct", minLiftPct);
		out.put("minSamplePerVariant", minSamplePerVariant);

		try {
			redisTemplate.opsForValue().set("admin:ab:test:" + testId + ":winner", winner);
			redisTemplate.opsForValue().set("admin:ab:test:" + testId + ":winner_reason", reason);
		} catch (Exception ignored) {
		}

		return out;
	}

	private long getLong(String key) {
		try {
			String v = redisTemplate.opsForValue().get(key);
			return v == null ? 0L : Long.parseLong(v);
		} catch (Exception e) {
			return 0L;
		}
	}

	private String normalizeVariant(String variant) {
		return "B".equalsIgnoreCase(variant) ? "B" : "A";
	}

	private double round2(double d) {
		return Math.round(d * 100.0) / 100.0;
	}

	private double round4(double d) {
		return Math.round(d * 10000.0) / 10000.0;
	}
}