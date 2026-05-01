package com.yogesh.logincontroller;

import com.yogesh.model.EmailVerificationToken;
import com.yogesh.model.User;
import com.yogesh.repository.EmailVerificationTokenRepository;
import com.yogesh.repository.UserRepository;
import com.yogesh.service.AccountService;
import com.yogesh.service.EmailService;
import com.yogesh.service.PasswordPolicyService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationTokenRepository tokenRepository;
	private final EmailService emailService;
	private final AccountService accountService;
	private final PasswordPolicyService passwordPolicyService;
	private final StringRedisTemplate redisTemplate;
	private final MessageSource messageSource;
	@Value("${google.oauth.client-id:${spring.security.oauth2.client.registration.google.client-id:}}")
	private String googleClientId;

	@GetMapping("/login")
	public String loginPage(HttpSession session) {
		if (session != null && Boolean.TRUE.equals(session.getAttribute("LOGIN_2FA_PENDING"))) {
			return "redirect:/login/otp";
		}
		return "login";
	}

	@GetMapping("/auth/google")
	public String startGoogleLogin(HttpServletRequest request, HttpServletResponse response) {
		if (!StringUtils.hasText(googleClientId)) {
			return "redirect:/login?googleDisabled";
		}

		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();

		Cookie sessionCookie = new Cookie("JSESSIONID", "");
		sessionCookie.setHttpOnly(true);
		sessionCookie.setPath("/");
		sessionCookie.setMaxAge(0);
		response.addCookie(sessionCookie);
		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
		response.setHeader("Pragma", "no-cache");

		return "redirect:/oauth2/authorization/google";
	}

	@GetMapping("/login/otp")
	public String loginOtpPage(Authentication authentication, HttpSession session, Model model) {
		if (authentication == null || session == null
				|| !Boolean.TRUE.equals(session.getAttribute("LOGIN_2FA_PENDING"))) {
			return "redirect:/login";
		}
		model.addAttribute("otpEmail", session.getAttribute("LOGIN_2FA_EMAIL"));
		return "login-otp";
	}

	@GetMapping("/register")
	public String showRegister(Model model) {
		model.addAttribute("user", new User());
		return "register";
	}

	@PostMapping("/api/auth/register")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> registerApi(@RequestBody Map<String, String> body, HttpServletRequest request) {
		try {
			String ip = getClientIp(request);
			if (isRateLimited("auth:register:ip:" + ip, 10, 60)) {
				return tooManyRequests(msg("auth.register.rateLimited.ip", "Bahut zyada register requests. 1 minute baad try karo."));
			}

			String name = body.get("name");
			String email = body.get("email");
			String password = body.get("password");
			String username = body.get("username");
			String interests = body.get("interests");
			String method = body.getOrDefault("verificationMethod", "link");

			if (name == null || name.isBlank()) return bad(msg("auth.register.name.required", "Naam daalo"));
			if (email == null || email.isBlank()) return bad(msg("auth.register.email.required", "Email daalo"));
			if (isRateLimited("auth:register:email:" + email.trim().toLowerCase(), 5, 60)) {
				return tooManyRequests(msg("auth.register.rateLimited.email", "Is email par abhi bahut attempts hain. 1 minute baad try karo."));
			}
			passwordPolicyService.validateOrThrow(password);
			if (userRepository.existsByEmail(email)) return bad(msg("auth.register.email.exists", "Yeh email already registered hai. Login karo."));
			if (username != null && !username.isBlank() && userRepository.existsByUsername(username.toLowerCase())) {
				return bad(msg("auth.register.username.exists", "Yeh username pehle se liya ja chuka hai"));
			}

			User user = new User();
			user.setName(name);
			user.setEmail(email);
			user.setPassword(passwordEncoder.encode(password));
			user.setRole("USER");
			user.setEnabled(false);
			user.setEmailVerified(false);

			if (interests != null && !interests.isBlank()) {
				user.setInterests(interests.split(","));
			}
			if (username != null && !username.isBlank()) {
				user.setUsername(username.toLowerCase().replaceAll("[^a-z0-9_.]", ""));
			}

			User saved = userRepository.save(user);

			if ("otp".equals(method)) {
				accountService.sendOtp(email, "VERIFY_EMAIL");
				log.info("OTP sent for registration: {}", email);
			} else {
				tokenRepository.deleteByUserId(saved.getId());
				String token = UUID.randomUUID().toString();
				EmailVerificationToken evt = new EmailVerificationToken();
				evt.setToken(token);
				evt.setUserId(saved.getId());
				tokenRepository.save(evt);
				emailService.sendVerificationEmail(email, name, token);
				log.info("Verification link sent for registration: {}", email);
			}

			return ok(Map.of(
					"success", true,
					"message", "otp".equals(method)
							? msg("auth.register.otp.sent", "OTP bhej diya!")
							: msg("auth.register.verification.sent", "Verification link bhej diya!"),
					"method", method,
					"email", email));
		} catch (Exception e) {
			log.error("Register error: {}", e.getMessage(), e);
			return bad(msg("auth.register.failed", "Registration fail ho gaya. Dobara try karo."));
		}
	}

	@PostMapping("/register")
	public String registerForm(@ModelAttribute User user, Model model) {
		if (userRepository.existsByEmail(user.getEmail())) {
			model.addAttribute("user", user);
			model.addAttribute("errorMsg", msg("auth.register.email.exists", "Yeh email already registered hai. Login karo."));
			return "register";
		}

		try {
			passwordPolicyService.validateOrThrow(user.getPassword());
		} catch (RuntimeException ex) {
			model.addAttribute("user", user);
			model.addAttribute("errorMsg", ex.getMessage());
			return "register";
		}

		user.setPassword(passwordEncoder.encode(user.getPassword()));
		user.setRole("USER");
		user.setEnabled(false);
		user.setEmailVerified(false);
		User saved = userRepository.save(user);

		String token = UUID.randomUUID().toString();
		EmailVerificationToken evt = new EmailVerificationToken();
		evt.setToken(token);
		evt.setUserId(saved.getId());
		tokenRepository.save(evt);
		emailService.sendVerificationEmail(user.getEmail(), user.getName(), token);

		return "redirect:/register?emailSent";
	}

	@GetMapping("/resend-verification")
	public String resendVerification(@RequestParam String email, Model model) {
		if (isRateLimited("auth:resend-verification:" + (email == null ? "unknown" : email.trim().toLowerCase()), 5, 60)) {
			model.addAttribute("errorMsg", msg("auth.verify.resend.rateLimited", "Bahut zyada resend attempts. 1 minute baad try karo."));
			return "verify-email";
		}

		var optUser = userRepository.findByEmail(email);
		if (optUser.isEmpty()) {
			model.addAttribute("errorMsg", msg("auth.verify.email.notRegistered", "Yeh email registered nahi hai."));
			return "verify-email";
		}

		User user = optUser.get();
		if (user.isEnabled()) {
			model.addAttribute("successMsg", msg("auth.verify.alreadyActive", "Account pehle se active hai! Login karo."));
			return "verify-email";
		}

		tokenRepository.deleteByUserId(user.getId());
		String token = UUID.randomUUID().toString();
		EmailVerificationToken evt = new EmailVerificationToken();
		evt.setToken(token);
		evt.setUserId(user.getId());
		tokenRepository.save(evt);
		emailService.sendVerificationEmail(user.getEmail(), user.getName(), token);

		model.addAttribute("successMsg", msg("auth.verify.resend.sent", "Naya verification link bhej diya!"));
		return "verify-email";
	}

	@PostMapping("/api/account/login-2fa/verify")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> verifyLoginOtp(@RequestBody Map<String, String> body,
			Authentication authentication, HttpServletRequest request) {
		try {
			HttpSession preSession = request.getSession(false);
			String emailKey = preSession != null && preSession.getAttribute("LOGIN_2FA_EMAIL") != null
					? preSession.getAttribute("LOGIN_2FA_EMAIL").toString().toLowerCase()
					: "unknown";
			if (isRateLimited("auth:login-otp-verify:" + emailKey, 12, 60)) {
				return tooManyRequests(msg("auth.login2fa.verify.rateLimited", "Too many OTP attempts. 1 minute baad try karo."));
			}

			HttpSession session = request.getSession(false);
			if (authentication == null || session == null
					|| !Boolean.TRUE.equals(session.getAttribute("LOGIN_2FA_PENDING"))) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg("auth.login2fa.session.missing", "2FA session nahi mili")));
			}

			String email = (String) session.getAttribute("LOGIN_2FA_EMAIL");
			String otp = body.get("otp");
			accountService.verifyOtp(email, "LOGIN_2FA", otp);

			session.removeAttribute("LOGIN_2FA_PENDING");
			session.removeAttribute("LOGIN_2FA_EMAIL");

			return ResponseEntity.ok(Map.of(
					"success", true,
					"message", msg("auth.login2fa.verify.success", "Login OTP verify ho gaya"),
					"redirect", resolveTarget(authentication)));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	@PostMapping("/api/account/login-2fa/resend")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> resendLoginOtp(Authentication authentication, HttpServletRequest request) {
		try {
			HttpSession session = request.getSession(false);
			if (authentication == null || session == null
					|| !Boolean.TRUE.equals(session.getAttribute("LOGIN_2FA_PENDING"))) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg("auth.login2fa.session.missing", "2FA session nahi mili")));
			}

			String email = (String) session.getAttribute("LOGIN_2FA_EMAIL");
			if (isRateLimited("auth:login-otp-resend:" + (email == null ? "unknown" : email.toLowerCase()), 5, 60)) {
				return tooManyRequests(msg("auth.login2fa.resend.rateLimited", "Bahut zyada resend attempts. 1 minute baad try karo."));
			}
			accountService.sendOtp(email, "LOGIN_2FA");
			return ResponseEntity.ok(Map.of("success", true, "message", msg("auth.login2fa.resend.success", "Naya OTP bhej diya")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	private String resolveTarget(Authentication authentication) {
		Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
		if (roles.contains("ROLE_ADMIN")) {
			return "/admin/dashboard";
		}
		return "/feed";
	}

	private ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
		return ResponseEntity.ok(body);
	}

	private ResponseEntity<Map<String, Object>> bad(String message) {
		return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
	}

	private ResponseEntity<Map<String, Object>> tooManyRequests(String message) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(Map.of("success", false, "message", message));
	}

	private boolean isRateLimited(String key, int limit, long windowSeconds) {
		try {
			Long count = redisTemplate.opsForValue().increment(key);
			if (count != null && count == 1L) {
				redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
			}
			return count != null && count > limit;
		} catch (Exception e) {
			// Redis down ho toh auth flows block na ho
			return false;
		}
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

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
