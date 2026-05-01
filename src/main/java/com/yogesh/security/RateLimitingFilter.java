package com.yogesh.security;

import com.yogesh.service.RateLimitService;
import com.yogesh.service.RateLimitService.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

	private final RateLimitService rateLimitService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		RateLimitRule rule = resolveRule(request);
		if (rule == null) {
			filterChain.doFilter(request, response);
			return;
		}

		String ip = getClientIp(request);
		String user = resolveUserIdentity(request);

		RateLimitResult ipResult = rateLimitService.consume(rule.scope() + ":ip", ip, rule.limit(), rule.window());
		if (!ipResult.allowed()) {
			writeTooManyRequests(response, request, ipResult.retryAfterSeconds());
			return;
		}

		RateLimitResult userResult = rateLimitService.consume(rule.scope() + ":user", user, rule.limit(), rule.window());
		if (!userResult.allowed()) {
			writeTooManyRequests(response, request, userResult.retryAfterSeconds());
			return;
		}

		filterChain.doFilter(request, response);
	}

	private RateLimitRule resolveRule(HttpServletRequest request) {
		String path = request.getRequestURI();
		String method = request.getMethod();

		if ("/login".equals(path) && "POST".equalsIgnoreCase(method)) {
			return new RateLimitRule("login", 5, Duration.ofMinutes(15));
		}

		if (isOtpPath(path)) {
			return new RateLimitRule("otp", 8, Duration.ofMinutes(5));
		}

		if (path.startsWith("/api/payment/")) {
			return new RateLimitRule("payment", 20, Duration.ofMinutes(1));
		}

		if (isPublicApi(path, method)) {
			return new RateLimitRule("public_api", 120, Duration.ofMinutes(1));
		}

		return null;
	}

	private boolean isOtpPath(String path) {
		return path.equals("/api/account/send-otp")
				|| path.equals("/api/account/verify-otp")
				|| path.equals("/api/account/login-2fa/verify")
				|| path.equals("/api/account/login-2fa/resend");
	}

	private boolean isPublicApi(String path, String method) {
		if (path.equals("/api/ai/status") || path.equals("/api/hybrid")
				|| path.startsWith("/api/presence/online/")) {
			return true;
		}

		if ("GET".equalsIgnoreCase(method) && path.equals("/api/live/active")) {
			return true;
		}

		return "GET".equalsIgnoreCase(method)
				&& path.startsWith("/api/live/")
				&& path.indexOf('/', "/api/live/".length()) < 0;
	}

	private String resolveUserIdentity(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
			return authentication.getName();
		}

		String username = request.getParameter("username");
		if (username != null && !username.isBlank()) {
			return username;
		}

		String email = request.getParameter("email");
		return email != null && !email.isBlank() ? email : "anonymous:" + getClientIp(request);
	}

	private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds)
			throws IOException {
		response.setStatus(429);
		response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many requests. Please try again later.\","
				+ "\"timestamp\":\"" + LocalDateTime.now() + "\",\"path\":\"" + request.getRequestURI() + "\"}");
	}

	private String getClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}

		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.trim();
		}

		return request.getRemoteAddr();
	}

	private record RateLimitRule(String scope, int limit, Duration window) {
	}
}
