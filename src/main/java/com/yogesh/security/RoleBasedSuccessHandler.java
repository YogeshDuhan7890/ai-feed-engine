package com.yogesh.security;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

	private final LoginTrackingService loginTrackingService;
	private final UserRepository userRepository;
	private final AccountService accountService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		try {
			loginTrackingService.trackLogin(request, authentication.getName());
		} catch (Exception e) {
			log.warn("Login tracking failed: {}", e.getMessage());
		}

		User user = userRepository.findByEmail(authentication.getName()).orElse(null);
		if (user != null && user.isTwoFaEnabled()) {
			try {
				accountService.sendOtp(user.getEmail(), "LOGIN_2FA");
				request.getSession(true).setAttribute("LOGIN_2FA_PENDING", true);
				request.getSession().setAttribute("LOGIN_2FA_EMAIL", user.getEmail());
				response.sendRedirect("/login/otp");
				return;
			} catch (Exception e) {
				log.error("Unable to start login 2FA for {}: {}", authentication.getName(), e.getMessage());
				response.sendRedirect("/login?error");
				return;
			}
		}

		if (request.getSession(false) != null) {
			request.getSession(false).removeAttribute("LOGIN_2FA_PENDING");
			request.getSession(false).removeAttribute("LOGIN_2FA_EMAIL");
		}

		Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
		if (roles.contains("ROLE_ADMIN")) {
			response.sendRedirect("/admin/dashboard");
		} else {
			response.sendRedirect("/feed");
		}
	}
}
