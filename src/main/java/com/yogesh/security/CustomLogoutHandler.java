package com.yogesh.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class CustomLogoutHandler implements LogoutHandler {

	private final StringRedisTemplate redisTemplate;

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response,
			org.springframework.security.core.Authentication authentication) {

		try {
			String sessionId = request.getSession().getId();

			// ✅ remove from active sessions
			redisTemplate.opsForSet().remove("admin:active:sessions", sessionId);

		} catch (Exception ignored) {
		}
	}
}