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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final LoginTrackingService loginTrackingService;
	private final AccountService accountService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
		String email = oauthUser.getAttribute("email");
		String name = oauthUser.getAttribute("name");
		String avatar = oauthUser.getAttribute("picture");

		if (email == null) {
			response.sendRedirect("/login?error");
			return;
		}

		User user = userRepository.findByEmail(email).orElse(null);
		boolean createdNow = false;
		if (user == null) {
			user = new User();
			user.setEmail(email);
			user.setName(name != null ? name : email.split("@")[0]);
			user.setPassword("");
			user.setRole("USER");
			user.setEnabled(true);
			user.setEmailVerified(true);
			user.setAvatar(avatar);
			user.setUsername(generateUniqueUsername(email, name));
			userRepository.save(user);
			createdNow = true;
		}

		CustomUserDetails userDetails = new CustomUserDetails(user);
		var authToken = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
				userDetails, null, userDetails.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authToken);

		try {
			loginTrackingService.trackLogin(request, email);
		} catch (Exception e) {
			log.warn("OAuth login tracking failed: {}", e.getMessage());
		}

		if (user.isTwoFaEnabled() || createdNow) {
			try {
				accountService.sendOtp(user.getEmail(), "LOGIN_2FA");
				request.getSession(true).setAttribute("LOGIN_2FA_PENDING", true);
				request.getSession().setAttribute("LOGIN_2FA_EMAIL", user.getEmail());
				response.sendRedirect("/login/otp");
				return;
			} catch (Exception e) {
				log.error("Unable to start OAuth 2FA for {}: {}", email, e.getMessage());
				response.sendRedirect("/login?error");
				return;
			}
		}

		if (request.getSession(false) != null) {
			request.getSession(false).removeAttribute("LOGIN_2FA_PENDING");
			request.getSession(false).removeAttribute("LOGIN_2FA_EMAIL");
		}

		Set<String> roles = AuthorityUtils.authorityListToSet(userDetails.getAuthorities());
		if (roles.contains("ROLE_ADMIN")) {
			response.sendRedirect("/admin/dashboard");
		} else {
			response.sendRedirect("/feed");
		}
	}

	private String generateUniqueUsername(String email, String name) {
		String seed = email != null && email.contains("@")
				? email.substring(0, email.indexOf('@'))
				: (name != null ? name : "user");
		String base = seed.toLowerCase().replaceAll("[^a-z0-9_.]", "");
		if (base.isBlank()) {
			base = "user";
		}
		if (base.length() > 32) {
			base = base.substring(0, 32);
		}
		if (base.length() < 3) {
			base = (base + "user").substring(0, 4);
		}

		String candidate = base;
		int suffix = 1;
		while (userRepository.existsByUsername(candidate)) {
			candidate = base + suffix;
			suffix++;
		}
		return candidate;
	}
}
