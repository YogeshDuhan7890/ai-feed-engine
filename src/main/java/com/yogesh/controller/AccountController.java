package com.yogesh.controller;

import com.yogesh.model.User;
import com.yogesh.repository.UserRepository;
import com.yogesh.security.CustomUserDetails;
import com.yogesh.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;
	private final UserRepository userRepository;
	private final MessageSource messageSource;

	// ─── Helper ──────────────────────────────────────────────────────────────

	private Long currentUserId(Authentication auth) {
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			throw new IllegalStateException("Unauthorized");
		}
		if (auth.getPrincipal() instanceof CustomUserDetails ud)
			return ud.getId();
		return userRepository.findByEmail(auth.getName()).orElseThrow().getId();
	}

	// ════════════════════════════════════════════════════════════════════════
	// PAGES
	// ════════════════════════════════════════════════════════════════════════

	@GetMapping("/account/settings")
	public String settingsPage(Authentication auth, Model model) {
		if (auth == null) {
			return "redirect:/login";
		}
		User user = userRepository.findByEmail(auth.getName()).orElseThrow();
		model.addAttribute("user", user);
		model.addAttribute("interestsText", user.getInterests() == null ? "" : String.join(",", user.getInterests()));
		return "account-settings";
	}

	@GetMapping("/forgot-password")
	public String forgotPasswordPage() {
		return "forgot-password";
	}

	@GetMapping("/reset-password")
	public String resetPasswordPage(@RequestParam String token, Model model) {
		model.addAttribute("token", token);
		return "reset-password";
	}

	@GetMapping("/verify-email")
	public String verifyEmailPage(@RequestParam String token, Model model) {
		try {
			accountService.verifyEmailLink(token);
			model.addAttribute("successMsg", msg("account.email.verified", "Email verify ho gaya! Ab login karo."));
		} catch (Exception e) {
			model.addAttribute("errorMsg", e.getMessage());
		}
		return "verify-email";
	}

	@GetMapping("/verify-email-change")
	public String verifyEmailChange(@RequestParam String token, Model model) {
		try {
			accountService.confirmEmailChange(token);
			model.addAttribute("successMsg",
					msg("account.email.change.verified", "Email successfully change ho gaya! Ab naye email se login karo."));
		} catch (Exception e) {
			model.addAttribute("errorMsg", e.getMessage());
		}
		return "verify-email";
	}

	// ════════════════════════════════════════════════════════════════════════
	// API — PASSWORD
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/api/account/change-password")
	@ResponseBody
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> body,
			Authentication auth) {
		try {
			Long userId = currentUserId(auth);
			accountService.changePassword(userId, body.get("currentPassword"), body.get("newPassword"));
			return ResponseEntity.ok(Map.of("success", true, "message", msg("account.password.changed", "Password change ho gaya")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	@PostMapping("/api/account/forgot-password")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
		try {
			accountService.sendPasswordResetLink(body.get("email"));
			// Always same message (security)
			return ResponseEntity.ok(Map.of("success", true, "message",
					msg("account.password.reset.sent", "Agar email registered hai to reset link bhej diya gaya hai")));
		} catch (Exception e) {
			return ResponseEntity.ok(Map.of("success", true, "message",
					msg("account.password.reset.sent", "Agar email registered hai to reset link bhej diya gaya hai")));
		}
	}

	@PostMapping("/api/account/reset-password")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
		try {
			accountService.resetPassword(body.get("token"), body.get("newPassword"));
			return ResponseEntity.ok(Map.of("success", true, "message", msg("account.password.reset.done", "Password reset ho gaya! Ab login karo.")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// API — PROFILE UPDATE
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/api/account/update-profile")
	@ResponseBody
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body,
			Authentication auth) {
		try {
			Long userId = currentUserId(auth);
			accountService.updateProfile(userId, body.get("name"), body.get("bio"), body.get("username"),
					body.get("interests"));
			return ResponseEntity.ok(Map.of("success", true, "message", msg("account.profile.updated", "Profile update ho gaya")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// API — EMAIL CHANGE
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/api/account/request-email-change")
	@ResponseBody
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Map<String, Object>> requestEmailChange(@RequestBody Map<String, String> body,
			Authentication auth) {
		try {
			Long userId = currentUserId(auth);
			accountService.requestEmailChange(userId, body.get("newEmail"));
			return ResponseEntity.ok(
					Map.of("success", true, "message", msg("account.email.change.sent", "Verification link naye email pe bhej diya. Check karo!")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// API — OTP
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/api/account/send-otp")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> body) {
		try {
			accountService.sendOtp(body.get("email"), body.get("purpose"));
			return ResponseEntity.ok(Map.of("success", true, "message", msg("account.otp.sent", "OTP bhej diya gaya")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	@PostMapping("/api/account/verify-otp")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
		try {
			accountService.verifyOtp(body.get("email"), body.get("purpose"), body.get("otp"));
			return ResponseEntity.ok(Map.of("success", true, "message", msg("account.otp.verified", "OTP verify ho gaya")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// API — 2FA
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/api/account/2fa/toggle")
	@ResponseBody
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Map<String, Object>> toggle2FA(@RequestBody Map<String, Object> body, Authentication auth) {
		try {
			Long userId = currentUserId(auth);
			boolean enable = Boolean.TRUE.equals(body.get("enable"));
			return ResponseEntity.ok(accountService.toggle2FA(userId, enable));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	@PostMapping("/api/account/privacy")
	@ResponseBody
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Map<String, Object>> updatePrivacy(@RequestBody Map<String, Object> body,
			Authentication auth) {
		try {
			Long userId = currentUserId(auth);
			boolean privateAccount = Boolean.TRUE.equals(body.get("privateAccount"));
			return ResponseEntity.ok(accountService.updatePrivacy(userId, privateAccount));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	// ════════════════════════════════════════════════════════════════════════
	// API — DELETE ACCOUNT
	// ════════════════════════════════════════════════════════════════════════

	@PostMapping("/api/account/delete")
	@ResponseBody
	@PreAuthorize("hasAnyRole('USER','ADMIN')")
	public ResponseEntity<Map<String, Object>> deleteAccount(@RequestBody Map<String, String> body, Authentication auth,
			HttpServletRequest request) {
		try {
			Long userId = currentUserId(auth);
			accountService.deleteAccount(userId, body.get("password"));

			// Session invalidate karo
			HttpSession session = request.getSession(false);
			if (session != null)
				session.invalidate();
			SecurityContextHolder.clearContext();

			return ResponseEntity
					.ok(Map.of("success", true, "message", msg("account.deleted", "Account delete ho gaya. Alvida!"), "redirect", "/login"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
		}
	}

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
