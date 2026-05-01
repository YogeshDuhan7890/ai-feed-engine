package com.yogesh.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TwoFactorEnforcementFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		HttpSession session = request.getSession(false);

		boolean authenticated = authentication != null
				&& authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
		boolean pendingTwoFactor = session != null && Boolean.TRUE.equals(session.getAttribute("LOGIN_2FA_PENDING"));

		if (authenticated && pendingTwoFactor && !isAllowed(request.getRequestURI())) {
			response.sendRedirect("/login/otp");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean isAllowed(String path) {
		return path.equals("/login/otp")
				|| path.startsWith("/api/account/login-2fa")
				|| path.startsWith("/css/")
				|| path.startsWith("/js/")
				|| path.startsWith("/images/")
				|| path.startsWith("/uploads/")
				|| path.startsWith("/fonts/")
				|| path.equals("/favicon.ico")
				|| path.equals("/manifest.json")
				|| path.equals("/sw.js")
				|| path.equals("/logout")
				|| path.startsWith("/error");
	}
}
