package com.yogesh.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import com.yogesh.model.User;
import com.yogesh.repository.NotificationRepository;
import com.yogesh.repository.UserRepository;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

	/*
	 * BUG FIX: "expected single matching bean but found 2" Fix: MailConfig mein
	 * zohoMailSender @Primary mark kiya — ab @RequiredArgsConstructor automatically
	 * zoho sender inject karega.
	 */
	private final JavaMailSender mailSender;
	private final UserRepository userRepository;
	private final NotificationRepository notificationRepository;

	@Value("${mail.zoho.username:}")
	private String fromEmail;

	@Value("${mail.zoho.host:smtp.zoho.in}")
	private String smtpHost;

	@Value("${mail.zoho.port:587}")
	private int smtpPort;

	@Value("${app.base-url:http://localhost:8080}")
	private String baseUrl;

	// ─── Welcome Email ───────────────────────────────────────────────────────
	@Async
	public void sendWelcomeEmail(String toEmail, String name) {
		sendHtml(toEmail, "AI Feed pe aapka swagat hai!", buildHtml("Namaste " + esc(name) + "!",
				"AI Feed mein aapka swagat hai. Apni pehli video upload karo!", new String[][] {
						{ "Feed Dekhein", baseUrl + "/feed" }, { "Video Upload Karein", baseUrl + "/upload" } },
				"Apna profile complete karein."));
	}

	// ─── New Follower Email ──────────────────────────────────────────────────
	@Async
	public void sendNewFollowerEmail(String toEmail, String toName, String followerName, Long followerId) {
		sendHtml(toEmail, followerName + " ne aapko follow kiya!",
				buildHtml(followerName + " aapko follow kar raha/rahi hai!",
						followerName + " ne abhi aapko follow kiya.",
						new String[][] { { "Profile Dekhein", baseUrl + "/profile/user/" + followerId },
								{ "Feed Dekhein", baseUrl + "/feed" } },
						"Unhe follow back karo!"));
	}

	// ─── New Like Email ──────────────────────────────────────────────────────
	@Async
	public void sendLikeEmail(String toEmail, String toName, String likerName, Long postId) {
		sendHtml(toEmail, likerName + " ne aapki video like ki!",
				buildHtml("Aapki video popular ho rahi hai!", likerName + " ne aapki video like ki.",
						new String[][] { { "Video Dekhein", baseUrl + "/feed?post=" + postId } },
						"Aur videos upload karo!"));
	}

	// ─── New Comment Email ───────────────────────────────────────────────────
	@Async
	public void sendCommentEmail(String toEmail, String toName, String commenterName, String commentText, Long postId) {
		String preview = commentText != null ? commentText.substring(0, Math.min(100, commentText.length())) : "";
		sendHtml(toEmail, commenterName + " ne comment kiya!",
				buildHtml(commenterName + " ne comment kiya:", "&ldquo;" + esc(preview) + "&rdquo;",
						new String[][] { { "Comment Dekhein", baseUrl + "/feed?post=" + postId } }, "Reply karo!"));
	}

	// ─── Password Reset Email ────────────────────────────────────────────────
	@Async
	public void sendPasswordResetEmail(String toEmail, String name, String resetToken) {
		String resetUrl = baseUrl + "/reset-password?token=" + resetToken;
		sendHtml(toEmail, "AI Feed - Password Reset",
				buildHtml("Password Reset Request", "Hamein aapke account ke liye password reset request mili.",
						new String[][] { { "Password Reset Karein", resetUrl } },
						"Yeh link 24 ghante mein expire ho jayega."));
	}

	// ─── Email Verification ──────────────────────────────────────────────────
	@Async
	public void sendVerificationEmail(String toEmail, String name, String token) {
		String verifyUrl = baseUrl + "/verify-email?token=" + token;
		sendHtml(toEmail, "AI Feed - Email Verify Karein",
				buildHtml("Email Verification", "Apna account activate karne ke liye button par click karein.",
						new String[][] { { "Email Verify Karein", verifyUrl } },
						"Agar aapne account nahi banaya toh ignore karein."));
	}

	// ─── Email Change Verification ───────────────────────────────────────────
	@Async
	public void sendEmailChangeVerification(String toEmail, String name, String token) {
		String verifyUrl = baseUrl + "/verify-email-change?token=" + token;
		sendHtml(toEmail, "AI Feed - Email Change Confirm Karein",
				buildHtml("Email Change Request",
						"Namaste " + esc(name) + "! Aapne apna email change karne ki request ki hai.",
						new String[][] { { "Email Change Confirm Karein", verifyUrl } },
						"Agar aapne yeh request nahi ki toh ignore karein. Link 24 ghante mein expire hoga."));
	}

	// ─── OTP Email ───────────────────────────────────────────────────────────
	@Async
	public void sendOtpEmail(String toEmail, String name, String otp, String purpose) {
		String purposeText = switch (purpose) {
		case "VERIFY_EMAIL" -> "Email verify karne ke liye";
		case "FORGOT_PASSWORD" -> "Password reset karne ke liye";
		case "LOGIN_2FA" -> "2FA login ke liye";
		default -> "Verification ke liye";
		};
		sendHtml(toEmail, "AI Feed - Aapka OTP: " + otp, buildHtml("Namaste " + esc(name) + "!",
				purposeText + " aapka OTP hai:" + "<div style='font-size:2.5rem;font-weight:900;letter-spacing:8px;"
						+ "color:#6366f1;text-align:center;margin:20px 0'>" + esc(otp) + "</div>"
						+ "Yeh OTP 10 minute mein expire ho jayega.",
				null, "Agar aapne yeh request nahi ki toh ignore karein."));
	}

	// ─── Warning Email ───────────────────────────────────────────────────────
	@Async
	public void sendWarningEmail(String toEmail, String name, String reason) {
		sendHtml(toEmail, "AI Feed - Account Warning",
				buildHtml("Account Warning Notice",
						"Aapke account pe warning issue ki gayi hai. Reason: " + esc(reason),
						new String[][] { { "Support Se Contact Karein", baseUrl + "/messages" } },
						"Agar galti hai toh support se contact karein."));
	}

	// ─── Verification approved / rejected ───────────────────────────────────
	@Async
	public void sendVerificationApprovedEmail(String toEmail, String name) {
		sendHtml(toEmail, "AI Feed - Verification Approved ✅",
				buildHtml("Congrats " + esc(name) + "!",
						"Aapka verification request approve ho gaya hai. Aapke profile pe verified badge dikh jayega.",
						new String[][] { { "Profile Dekhein", baseUrl + "/profile/me" } },
						"Thank you for building on AI Feed."));
	}

	@Async
	public void sendVerificationRejectedEmail(String toEmail, String name, String reason) {
		String r = (reason == null || reason.isBlank()) ? "Incomplete details" : reason;
		sendHtml(toEmail, "AI Feed - Verification Update",
				buildHtml("Hello " + esc(name) + "!",
						"Aapka verification request abhi approve nahi ho paya. Reason: " + esc(r)
								+ "<br/><br/>Aap details update karke dobara request submit kar sakte ho.",
						new String[][] { { "Request Again", baseUrl + "/settings" } },
						"Support chahiye toh reply karo."));
	}

	// ─── Weekly Digest ───────────────────────────────────────────────────────
	@Async
	public void sendWeeklyDigest(String toEmail, String name, long newFollowers, long totalLikes, long unreadNotifs) {
		String stats = "<div style='display:flex;gap:16px;flex-wrap:wrap;margin:20px 0;justify-content:center'>"
				+ stat(String.valueOf(newFollowers), "Naye Followers", "#6366f1")
				+ stat(String.valueOf(totalLikes), "Total Likes", "#f43f5e")
				+ stat(String.valueOf(unreadNotifs), "Notifications", "#10b981") + "</div>";
		sendHtml(toEmail, "AI Feed - Is Hafte Ki Summary",
				buildHtml("Namaste " + esc(name) + "!", stats, new String[][] {
						{ "Analytics Dekhein", baseUrl + "/analytics" }, { "Feed Dekhein", baseUrl + "/feed" } },
						"Aur content banao aur audience badhaao!"));
	}

	private String stat(String value, String label, String color) {
		return "<div style='text-align:center;background:#f8f9fa;padding:14px 20px;border-radius:12px;min-width:100px'>"
				+ "<div style='font-size:1.8rem;font-weight:800;color:" + color + "'>" + esc(value) + "</div>"
				+ "<div style='color:#6b7280;font-size:.8rem'>" + esc(label) + "</div>" + "</div>";
	}

	// ─── Scheduled: Weekly digest every Sunday 9AM IST ───────────────────────
	@Scheduled(cron = "0 0 9 * * SUN", zone = "Asia/Kolkata")
	public void scheduleWeeklyDigests() {
		if (fromEmail == null || fromEmail.isBlank()) {
			log.warn("Email not configured - skipping weekly digest");
			return;
		}
		List<User> users = userRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
		int sent = 0;
		for (User user : users) {
			if (user.getEmail() == null || !user.isEnabled())
				continue;
			try {
				long unread = notificationRepository.countByToUserIdAndReadFalse(user.getId());
				if (unread == 0)
					continue;
				sendWeeklyDigest(user.getEmail(), user.getName(), 0, 0, unread);
				sent++;
				Thread.sleep(300);
			} catch (Exception e) {
				log.warn("Digest fail for {}: {}", user.getEmail(), e.getMessage());
			}
		}
		log.info("Weekly digest sent to {} users", sent);
	}

	// ─── Core send ───────────────────────────────────────────────────────────
	@Async
	public void sendHtml(String to, String subject, String html) {
		if (fromEmail == null || fromEmail.isBlank()) {
			log.warn("Email not configured - skipping: {}", subject);
			return;
		}
		if (to == null || to.isBlank())
			return;
		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
			helper.setFrom(fromEmail, "AI Feed");
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(html, true);
			mailSender.send(msg);
			log.info("Email sent: {} -> {}", subject, to);
		} catch (Exception e) {
			log.error("Email failed to {}: {}", to, e.getMessage());
		}
	}

	// ─── HTML Template ────────────────────────────────────────────────────────
	private String buildHtml(String heading, String body, String[][] btns, String footer) {
		StringBuilder btnHtml = new StringBuilder();
		if (btns != null) {
			for (String[] b : btns) {
				btnHtml.append("<a href='" + b[1] + "' style='"
						+ "display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);"
						+ "color:#fff;text-decoration:none;padding:11px 22px;"
						+ "border-radius:24px;font-weight:700;font-size:14px;margin:6px'>" + esc(b[0]) + "</a>");
			}
		}
		return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
				+ "<meta name='viewport' content='width=device-width,initial-scale=1'></head>"
				+ "<body style='margin:0;padding:0;background:#f4f4f8;font-family:Segoe UI,Arial,sans-serif'>"
				+ "<div style='max-width:540px;margin:32px auto;background:#fff;border-radius:16px;"
				+ "overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,.1)'>"
				+ "<div style='background:linear-gradient(135deg,#07070f,#1a1a2e);padding:24px;text-align:center'>"
				+ "<div style='font-size:1.5rem;font-weight:900;color:#f1f1f8'>"
				+ "<span style='color:#6366f1'>AI</span> Feed</div></div>" + "<div style='padding:28px'>"
				+ "<h2 style='color:#1a1a2e;font-size:1.2rem;margin:0 0 12px;font-weight:800'>" + heading + "</h2>"
				+ "<div style='color:#6b7280;font-size:.9rem;line-height:1.6;margin-bottom:20px'>" + body + "</div>"
				+ "<div style='text-align:center;margin:20px 0'>" + btnHtml + "</div>" + "</div>"
				+ "<div style='background:#f8f9fa;padding:18px 28px;border-top:1px solid #e5e7eb'>"
				+ "<p style='color:#9ca3af;font-size:.75rem;margin:0 0 4px'>" + esc(footer != null ? footer : "")
				+ "</p>" + "<p style='color:#d1d5db;font-size:.7rem;margin:0'>&copy; 2025 AI Feed &nbsp;|&nbsp;"
				+ "<a href='" + baseUrl + "/settings' style='color:#6366f1;text-decoration:none'>Settings</a>"
				+ " &nbsp;|&nbsp;" + "<a href='" + baseUrl
				+ "/unsubscribe' style='color:#9ca3af;text-decoration:none'>Unsubscribe</a>"
				+ "</p></div></div></body></html>";
	}

	private String esc(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&#39;");
	}

	public Map<String, Object> mailHealth() {
		boolean configured = fromEmail != null && !fromEmail.isBlank();
		return Map.of(
				"configured", configured,
				"fromEmailMasked", maskEmail(fromEmail),
				"smtpHost", smtpHost,
				"smtpPort", smtpPort,
				"baseUrl", baseUrl);
	}

	private String maskEmail(String email) {
		if (email == null || email.isBlank() || !email.contains("@")) {
			return "";
		}
		String[] parts = email.split("@", 2);
		String local = parts[0];
		String domain = parts[1];
		if (local.length() <= 2) {
			return "*@" + domain;
		}
		return local.substring(0, 2) + "***@" + domain;
	}
}