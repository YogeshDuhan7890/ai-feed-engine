package com.yogesh.security;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	private final IpBanFilter ipBanFilter;
	private final UserRepository userRepository;

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {

		try {
			ipBanFilter.recordFailedLogin(resolveClientIp(request));
		} catch (Exception e) {
			log.debug("Failed login tracking skipped: {}", e.getMessage());
		}

		String redirectUrl = "/login?error";
		String loginId = request.getParameter("username");
		String normalizedLoginId = loginId == null ? "" : loginId.trim();

		if (exception instanceof DisabledException) {
			redirectUrl = "/login?disabled";
		} else if (!normalizedLoginId.isBlank()) {
			User user = userRepository.findByEmailIgnoreCase(normalizedLoginId)
					.or(() -> userRepository.findByUsernameIgnoreCase(normalizedLoginId))
					.orElse(null);
			if (user != null && (user.getPassword() == null || user.getPassword().isBlank())) {
				redirectUrl = "/login?googleOnly";
			}
		}

		setDefaultFailureUrl(redirectUrl);
		super.onAuthenticationFailure(request, response, exception);
	}

	private String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}

		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.trim();
		}

		return request.getRemoteAddr();
	}
}
