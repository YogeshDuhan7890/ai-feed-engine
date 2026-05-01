package com.yogesh.logincontroller;

import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.yogesh.service.FeatureFlagService;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

	private static final List<String> BYPASS_PREFIXES = List.of(
			"/admin",
			"/api/admin",
			"/css/",
			"/js/",
			"/images/",
			"/uploads/",
			"/fonts/",
			"/actuator/");

	private static final Set<String> BYPASS_EXACT = Set.of(
			"/maintenance",
			"/offline.html",
			"/login",
			"/register",
			"/auth/google",
			"/logout",
			"/sw.js",
			"/favicon.ico",
			"/manifest.json",
			"/api/push/generate-keys");

	private final FeatureFlagService flags;

	@Override
	public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
		String uri = req.getRequestURI();
		boolean maintenance = flags.isEnabled("maintenance:mode");
		if (maintenance && !shouldBypass(uri)) {
			String msg = flags.getString("maintenance:message", "Maintenance ongoing");
			if (expectsHtml(req)) {
				res.sendRedirect("/maintenance");
				return false;
			}

			res.setStatus(503);
			res.setCharacterEncoding("UTF-8");
			res.setContentType("application/json");
			res.getWriter().write("{\"error\":\"" + msg.replace("\"", "") + "\"}");
			return false;
		}
		return true;
	}

	private boolean shouldBypass(String uri) {
		if (uri == null || uri.isBlank()) {
			return false;
		}
		if (BYPASS_EXACT.contains(uri)) {
			return true;
		}
		return BYPASS_PREFIXES.stream().anyMatch(uri::startsWith);
	}

	private boolean expectsHtml(HttpServletRequest req) {
		String accept = req.getHeader("Accept");
		String requestedWith = req.getHeader("X-Requested-With");
		String fetchMode = req.getHeader("Sec-Fetch-Mode");
		String fetchDest = req.getHeader("Sec-Fetch-Dest");
		boolean ajax = requestedWith != null && requestedWith.equalsIgnoreCase("XMLHttpRequest");
		boolean html = accept != null
				&& (accept.contains("text/html") || accept.contains("application/xhtml+xml"));
		boolean navigation = "navigate".equalsIgnoreCase(fetchMode) || "document".equalsIgnoreCase(fetchDest);
		String method = req.getMethod();
		boolean safeMethod = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
		return safeMethod && !ajax && (html || navigation);
	}
}
