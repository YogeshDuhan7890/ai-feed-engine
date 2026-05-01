package com.yogesh.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpBanFilter extends OncePerRequestFilter {

	private final StringRedisTemplate redisTemplate;

	private static final int MAX_REQUESTS_PER_MIN = 200;
	private static final int MAX_LOGIN_ATTEMPTS = 5;

	private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
	private static final Duration LOGIN_LOCKOUT = Duration.ofMinutes(15);

	private static final Duration AUTO_BAN_RATE = Duration.ofMinutes(10);
	private static final Duration AUTO_BAN_LOGIN = Duration.ofMinutes(30);
	private static final Duration[] IP_BAN_LADDER = new Duration[] {
			Duration.ofMinutes(30),
			Duration.ofHours(2),
			Duration.ofHours(24),
			Duration.ofDays(30)
	};
	private static final Duration LOCAL_BAN_FALLBACK = Duration.ofHours(2);
	private final Map<String, Long> localBanUntilEpochMs = new ConcurrentHashMap<>();

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {

		String ip = getClientIP(req);
		String path = req.getRequestURI();

		// 🔥 1. HARD BAN CHECK (admin + auto)
		if (isIpBanned(ip)) {
			block(res, "IP banned");
			return;
		}

		// 🔥 2. LOGIN LOCK CHECK
		if (isLoginRequest(req)) {
			if (Boolean.TRUE.equals(redisTemplate.hasKey("login:locked:" + ip))) {
				block(res, "Too many login attempts. 15 min wait karo.", 429);
				return;
			}
		}

		// 🔥 3. RATE LIMIT (only API / auth endpoints)
		if (shouldSkipRateLimit(path)) {
			chain.doFilter(req, res);
			return;
		}

		try {
			String rateKey = "rate:" + ip;

			Long count = redisTemplate.opsForValue().increment(rateKey);

			if (count != null && count == 1) {
				redisTemplate.expire(rateKey, RATE_WINDOW);
			}

			// 🚨 RATE LIMIT HIT → AUTO BAN
			if (count != null && count > MAX_REQUESTS_PER_MIN) {

				log.warn("AUTO BAN (rate): ip={} count={}", ip, count);
				Duration ttl = nextIpBanDuration(ip);
				banIp(ip, "Auto ban: rate limit", ttl);

				block(res, "Rate limit exceeded. IP temporarily banned.", 429);
				return;
			}

		} catch (Exception e) {
			log.debug("Redis error (rate limit): {}", e.getMessage());
		}

		chain.doFilter(req, res);
	}

	/*
	 * ================================ LOGIN FAIL TRACKING (AUTO BAN)
	 * =================================
	 */

	public void recordFailedLogin(String ip) {

		try {
			String key = "login:fail:" + ip;

			Long attempts = redisTemplate.opsForValue().increment(key);

			if (attempts != null && attempts == 1) {
				redisTemplate.expire(key, LOGIN_LOCKOUT);
			}

			if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {

				log.warn("AUTO BAN (login brute): ip={} attempts={}", ip, attempts);
				Duration ttl = nextIpBanDuration(ip);
				banIp(ip, "Auto ban: brute force", ttl);

				redisTemplate.delete(key);
			}

		} catch (Exception e) {
			log.debug("Login tracking error: {}", e.getMessage());
		}
	}

	/*
	 * ================================ HELPERS =================================
	 */

	private boolean isLoginRequest(HttpServletRequest req) {
		return "/login".equals(req.getRequestURI()) && "POST".equalsIgnoreCase(req.getMethod());
	}

	private boolean shouldSkipRateLimit(String path) {
		return !(path.startsWith("/api/") || path.startsWith("/login") || path.startsWith("/register"));
	}

	private void block(HttpServletResponse res, String message) throws IOException {
		block(res, message, 403);
	}

	private void block(HttpServletResponse res, String message, int status) throws IOException {
		res.setStatus(status);
		res.setContentType("application/json");
		res.getWriter().write("{\"error\":\"" + message + "\"}");
	}

	private String getClientIP(HttpServletRequest req) {
		String remoteAddr = req.getRemoteAddr();

		String xff = req.getHeader("X-Forwarded-For");
		if (isTrustedProxy(remoteAddr) && xff != null && !xff.isBlank()) {
			String[] parts = xff.split(",");
			for (int i = parts.length - 1; i >= 0; i--) {
				String candidate = parts[i].trim();
				if (!candidate.isBlank()) {
					return candidate;
				}
			}
		}

		String realIp = req.getHeader("X-Real-IP");
		if (isTrustedProxy(remoteAddr) && realIp != null && !realIp.isBlank()) {
			return realIp;
		}

		return remoteAddr;
	}

	private boolean isTrustedProxy(String remoteAddr) {
		if (remoteAddr == null || remoteAddr.isBlank()) {
			return false;
		}
		return remoteAddr.equals("127.0.0.1")
				|| remoteAddr.equals("0:0:0:0:0:0:0:1")
				|| remoteAddr.equals("::1")
				|| remoteAddr.startsWith("10.")
				|| remoteAddr.startsWith("192.168.")
				|| remoteAddr.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
	}

	private Duration nextIpBanDuration(String ip) {
		try {
			String countKey = "ban:count:ip:" + ip;
			Long count = redisTemplate.opsForValue().increment(countKey);
			redisTemplate.expire(countKey, Duration.ofDays(30));
			int idx = count == null ? 0 : (int) Math.max(0, Math.min(count - 1, IP_BAN_LADDER.length - 1));
			return IP_BAN_LADDER[idx];
		} catch (Exception ignored) {
			return AUTO_BAN_LOGIN;
		}
	}

	private boolean isIpBanned(String ip) {
		long now = System.currentTimeMillis();
		Long localUntil = localBanUntilEpochMs.get(ip);
		if (localUntil != null) {
			if (localUntil > now) {
				return true;
			}
			localBanUntilEpochMs.remove(ip);
		}

		try {
			String redisKey = "admin:ip:banned:" + ip;
			if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
				Long ttlSec = redisTemplate.getExpire(redisKey);
				long ttlMs = (ttlSec != null && ttlSec > 0) ? ttlSec * 1000 : LOCAL_BAN_FALLBACK.toMillis();
				localBanUntilEpochMs.put(ip, now + ttlMs);
				return true;
			}
		} catch (Exception e) {
			log.debug("Redis error while checking ban key: {}", e.getMessage());
		}

		return false;
	}

	private void banIp(String ip, String reason, Duration ttl) {
		long safeTtlMs = (ttl == null || ttl.isNegative() || ttl.isZero()) ? LOCAL_BAN_FALLBACK.toMillis() : ttl.toMillis();
		localBanUntilEpochMs.put(ip, System.currentTimeMillis() + safeTtlMs);
		try {
			redisTemplate.opsForValue().set("admin:ip:banned:" + ip, reason, ttl != null ? ttl : LOCAL_BAN_FALLBACK);
		} catch (Exception e) {
			log.warn("Redis ban persist failed for ip={}: {}", ip, e.getMessage());
		}
	}
}
