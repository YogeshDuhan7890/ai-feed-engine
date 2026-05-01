package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAttributes {

	private final UserRepository userRepository;
	@Value("${google.oauth.client-id:${spring.security.oauth2.client.registration.google.client-id:}}")
	private String googleClientId;

	@ModelAttribute("currentUserRole")
	public String currentUserRole(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return "GUEST";
		}

		return userRepository.findByEmail(authentication.getName())
				.map(User::getRole)
				.orElse("USER");
	}

	@ModelAttribute("isAuthenticatedUser")
	public boolean isAuthenticatedUser(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(String.valueOf(authentication.getPrincipal()));
	}

	@ModelAttribute("isAdminUser")
	public boolean isAdminUser(@ModelAttribute("currentUserRole") String role) {
		return "ADMIN".equalsIgnoreCase(role);
	}

	@ModelAttribute("isStaffUser")
	public boolean isStaffUser(@ModelAttribute("currentUserRole") String role) {
		return "ADMIN".equalsIgnoreCase(role);
	}

	@ModelAttribute("googleLoginEnabled")
	public boolean googleLoginEnabled() {
		return StringUtils.hasText(googleClientId);
	}
}
