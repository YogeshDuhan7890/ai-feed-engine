package com.yogesh.service;

import com.yogesh.model.*;
import com.yogesh.repository.*;
import com.yogesh.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository resetTokenRepository;
	private final EmailVerificationTokenRepository verificationTokenRepository;
	private final PostRepository postRepository;
	private final CommentRepository commentRepository;
	private final FollowRepository followRepository;
	private final NotificationRepository notificationRepository;
	private final DirectMessageRepository directMessageRepository;
	private final StoryRepository storyRepository;
	private final BookmarkRepository bookmarkRepository;
	private final PushSubscriptionRepository pushSubscriptionRepository;
	private final BlockRepository blockRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final PasswordPolicyService passwordPolicyService;
	private final StringRedisTemplate redisTemplate;
	private final MessageSource messageSource;

	private static final Duration OTP_EXPIRY = Duration.ofMinutes(5);
	private static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
	private static final int OTP_MAX_ATTEMPTS = 5;
	private static final String OTP_FIELD_CODE = "code";
	private static final String OTP_FIELD_ATTEMPTS = "attempts";

	// ─── Change Password ─────────────────────────────────────────────────────

	public void changePassword(Long userId, String currentPassword, String newPassword) {
		User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException(msg("account.user.notFound", "User nahi mila")));

		if (!passwordEncoder.matches(currentPassword, user.getPassword()))
			throw new RuntimeException(msg("account.password.current.invalid", "Purana password galat hai"));
		passwordPolicyService.validateOrThrow(newPassword);

		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		log.info("Password changed for userId={}", userId);
	}

	// ─── Forgot Password ─────────────────────────────────────────────────────

	@Transactional
	public void sendPasswordResetLink(String email) {
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null)
			return; 

		resetTokenRepository.deleteByUserId(user.getId());

		String token = UUID.randomUUID().toString();
		PasswordResetToken prt = new PasswordResetToken();
		prt.setToken(token);
		prt.setUserId(user.getId());
		resetTokenRepository.save(prt);

		emailService.sendPasswordResetEmail(email, user.getName() != null ? user.getName() : "User", token);
		log.info("Password reset link sent to={}", email);
	}

	// ─── Reset Password ──────────────────────────────────────────────────────

	@Transactional
	public void resetPassword(String token, String newPassword) {
		PasswordResetToken prt = resetTokenRepository.findByToken(token)
				.orElseThrow(() -> new RuntimeException(msg("account.reset.invalidLink", "Invalid reset link")));

		if (prt.isUsed())
			throw new RuntimeException("Yeh link pehle se use ho chuka hai");
		if (prt.isExpired())
			throw new RuntimeException("Reset link expire ho gaya (30 minute). Dobara request karo.");
		passwordPolicyService.validateOrThrow(newPassword);

		User user = userRepository.findById(prt.getUserId()).orElseThrow();
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);

		prt.setUsed(true);
		resetTokenRepository.save(prt);
		log.info("Password reset for userId={}", prt.getUserId());
	}

	// ─── Update Profile ──────────────────────────────────────────────────────

	public void updateProfile(Long userId, String name, String bio, String username, String interests) {
		User user = userRepository.findById(userId).orElseThrow();

		if (name != null && !name.isBlank())
			user.setName(name);
		if (bio != null)
			user.setBio(bio);

		if (interests != null) {
			user.setInterests(interests.isBlank() ? new String[0] : interests.split(","));
		}

		if (username != null && !username.isBlank()) {
			String clean = username.toLowerCase().replaceAll("[^a-z0-9_.]", "");
			if (clean.length() < 3)
				throw new RuntimeException("Username kam se kam 3 characters ka hona chahiye");
			if (!clean.equals(user.getUsername())) {
				if (userRepository.existsByUsername(clean))
					throw new RuntimeException("Yeh username pehle se liya ja chuka hai");
				user.setUsername(clean);
			}
		}

		userRepository.save(user);
	}

	// ─── Request Email Change ─────────────────────────────────────────────────

	@Transactional
	public void requestEmailChange(Long userId, String newEmail) {
		if (userRepository.existsByEmail(newEmail))
			throw new RuntimeException(msg("account.email.alreadyUsed", "Yeh email pehle se kisi aur ka hai"));

		User user = userRepository.findById(userId).orElseThrow();
		user.setPendingEmail(newEmail);
		userRepository.save(user);

		verificationTokenRepository.deleteByUserId(userId);
		String token = UUID.randomUUID().toString();
		EmailVerificationToken evt = new EmailVerificationToken();
		evt.setToken(token);
		evt.setUserId(userId);
		verificationTokenRepository.save(evt);

		emailService.sendEmailChangeVerification(newEmail, user.getName() != null ? user.getName() : "User", token);
		log.info("Email change requested userId={} newEmail={}", userId, newEmail);
	}

	// ─── Confirm Email Change ────────────────────────────────────────────────

	@Transactional
	public void confirmEmailChange(String token) {
		EmailVerificationToken evt = verificationTokenRepository.findByToken(token)
				.orElseThrow(() -> new RuntimeException(msg("account.verify.invalidLink", "Invalid link")));

		if (evt.isUsed())
			throw new RuntimeException("Link pehle se use ho chuka hai");
		if (evt.isExpired())
			throw new RuntimeException("Link expire ho gaya. Dobara request karo.");

		User user = userRepository.findById(evt.getUserId()).orElseThrow();
		if (user.getPendingEmail() == null)
			throw new RuntimeException("Koi pending email change nahi hai");

		user.setEmail(user.getPendingEmail());
		user.setPendingEmail(null);
		userRepository.save(user);

		evt.setUsed(true);
		verificationTokenRepository.save(evt);
		log.info("Email changed for userId={}", user.getId());
	}

	// ─── Send OTP ────────────────────────────────────────────────────────────

	@Transactional
	public void sendOtp(String email, String purpose) {
		String normalizedEmail = normalizeEmail(email);
		String normalizedPurpose = normalizePurpose(purpose);
		enforceOtpCooldown(normalizedEmail, normalizedPurpose);

		String otp = generateOtp();
		String otpKey = RedisKeys.otpToken(normalizedEmail, normalizedPurpose);
		redisTemplate.opsForHash().putAll(otpKey, Map.of(
				OTP_FIELD_CODE, otp,
				OTP_FIELD_ATTEMPTS, "0"));
		redisTemplate.expire(otpKey, OTP_EXPIRY);
		redisTemplate.opsForValue().set(RedisKeys.otpCooldown(normalizedEmail, normalizedPurpose), "1", OTP_COOLDOWN);

		User user = findUserByEmailAnyCase(email, normalizedEmail);
		String name = (user != null && user.getName() != null) ? user.getName() : "User";

		emailService.sendOtpEmail(email.trim(), name, otp, normalizedPurpose);
		log.info("OTP sent email={} purpose={} ttlSeconds={}", normalizedEmail, normalizedPurpose, OTP_EXPIRY.toSeconds());
	}

	// ─── Verify OTP ──────────────────────────────────────────────────────────

	@Transactional
	public boolean verifyOtp(String email, String purpose, String enteredOtp) {
		String normalizedEmail = normalizeEmail(email);
		String normalizedPurpose = normalizePurpose(purpose);
		String otpKey = RedisKeys.otpToken(normalizedEmail, normalizedPurpose);

		Object storedOtp = redisTemplate.opsForHash().get(otpKey, OTP_FIELD_CODE);
		if (storedOtp == null)
			throw new RuntimeException("OTP nahi mila ya expire ho gaya. Pehle OTP mangwao.");

		int attempts = parseAttempts(redisTemplate.opsForHash().get(otpKey, OTP_FIELD_ATTEMPTS));
		if (attempts >= OTP_MAX_ATTEMPTS)
			throw new RuntimeException("5 galat attempts - OTP block ho gaya. Naya OTP mangwao.");

		if (!storedOtp.toString().equals(enteredOtp)) {
			Long updatedAttempts = redisTemplate.opsForHash().increment(otpKey, OTP_FIELD_ATTEMPTS, 1);
			int remaining = OTP_MAX_ATTEMPTS - (updatedAttempts == null ? attempts + 1 : updatedAttempts.intValue());
			throw new RuntimeException(
					"Galat OTP. " + (remaining > 0 ? remaining + " attempts bache hain." : "OTP block ho gaya."));
		}

		redisTemplate.delete(otpKey);

		if ("VERIFY_EMAIL".equals(normalizedPurpose)) {
			User user = findUserByEmailAnyCase(email, normalizedEmail);
			if (user != null) {
				User u = user;
				u.setEnabled(true);
				u.setEmailVerified(true);
				userRepository.save(u);
			}
		}

		return true;
	}

	// ─── Delete Account ───────────────────────────────────────────────────────

	@Transactional
	public void deleteAccount(Long userId, String password) {
		User user = userRepository.findById(userId).orElseThrow();
		if (!passwordEncoder.matches(password, user.getPassword()))
			throw new RuntimeException("Password galat hai. Account delete nahi hua.");

		Set<Long> affectedUserIds = new HashSet<>();
		affectedUserIds.add(userId);
		followRepository.findByFollowerIdOrFollowingId(userId, userId).forEach(follow -> {
			if (follow.getFollowerId() != null) {
				affectedUserIds.add(follow.getFollowerId());
			}
			if (follow.getFollowingId() != null) {
				affectedUserIds.add(follow.getFollowingId());
			}
		});

		commentRepository.deleteByUserId(userId);
		bookmarkRepository.deleteByUserId(userId);
		postRepository.deleteByUserId(userId);
		directMessageRepository.deleteByUserId(userId);
		storyRepository.deleteByUserId(userId);
		notificationRepository.deleteByToUserIdOrFromUserId(userId, userId);
		followRepository.deleteByFollowerIdOrFollowingId(userId, userId);
		pushSubscriptionRepository.deleteByUserId(userId);
		blockRepository.deleteByBlockerIdOrBlockedId(userId, userId);

		resetTokenRepository.deleteByUserId(userId);
		verificationTokenRepository.deleteByUserId(userId);
		deleteOtpKeys(user.getEmail(), "VERIFY_EMAIL");
		deleteOtpKeys(user.getEmail(), "CHANGE_EMAIL");
		deleteOtpKeys(user.getEmail(), "TWO_FA");
		deleteOtpKeys(user.getEmail(), "LOGIN");
		deleteOtpKeys(user.getEmail(), "LOGIN_2FA");
		userRepository.delete(user);
		syncFollowCounters(affectedUserIds);
		log.info("Account deleted userId={}", userId);
	}

	// ─── 2FA Toggle ──────────────────────────────────────────────────────────

	public Map<String, Object> toggle2FA(Long userId, boolean enable) {
		User user = userRepository.findById(userId).orElseThrow();
		user.setTwoFaEnabled(enable);
		if (!enable)
			user.setTwoFaSecret(null);
		userRepository.save(user);
		return Map.of("enabled", enable, "message", enable ? "2FA on ho gaya" : "2FA off ho gaya");
	}

	public Map<String, Object> updatePrivacy(Long userId, boolean privateAccount) {
		User user = userRepository.findById(userId).orElseThrow();
		user.setPrivateAccount(privateAccount);
		userRepository.save(user);
		return Map.of(
				"success", true,
				"privateAccount", privateAccount,
				"message", privateAccount ? "Private account on ho gaya" : "Private account off ho gaya");
	}

	// ─── Verify Email (link) ─────────────────────────────────────────────────

	@Transactional
	public void verifyEmailLink(String token) {
		EmailVerificationToken evt = verificationTokenRepository.findByToken(token)
				.orElseThrow(() -> new RuntimeException(msg("account.verify.invalidLink", "Invalid link")));

		if (evt.isUsed())
			throw new RuntimeException("Link pehle se use ho chuka hai");
		if (evt.isExpired())
			throw new RuntimeException("Link expire ho gaya. Naya link mangwao.");

		userRepository.findById(evt.getUserId()).ifPresent(u -> {
			u.setEnabled(true);
			u.setEmailVerified(true);
			userRepository.save(u);
		});

		evt.setUsed(true);
		verificationTokenRepository.save(evt);
	}

	// ─── Helper ──────────────────────────────────────────────────────────────

	private String generateOtp() {
		return String.format("%06d", new SecureRandom().nextInt(1_000_000));
	}

	private void enforceOtpCooldown(String email, String purpose) {
		String cooldownKey = RedisKeys.otpCooldown(email, purpose);
		Boolean cooldownActive = redisTemplate.hasKey(cooldownKey);
		if (Boolean.TRUE.equals(cooldownActive)) {
			Long ttl = redisTemplate.getExpire(cooldownKey);
			String wait = ttl != null && ttl > 0 ? ttl + " seconds" : "60 seconds";
			throw new RuntimeException("OTP dobara bhejne se pehle " + wait + " wait karo.");
		}
	}

	private void deleteOtpKeys(String email, String purpose) {
		String normalizedEmail = normalizeEmail(email);
		String normalizedPurpose = normalizePurpose(purpose);
		redisTemplate.delete(RedisKeys.otpToken(normalizedEmail, normalizedPurpose));
		redisTemplate.delete(RedisKeys.otpCooldown(normalizedEmail, normalizedPurpose));
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new RuntimeException("Email required hai");
		}
		return email.trim().toLowerCase();
	}

	private String normalizePurpose(String purpose) {
		if (purpose == null || purpose.isBlank()) {
			throw new RuntimeException("OTP purpose required hai");
		}
		return purpose.trim().toUpperCase();
	}

	private int parseAttempts(Object attempts) {
		if (attempts == null) {
			return 0;
		}
		try {
			return Integer.parseInt(attempts.toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private User findUserByEmailAnyCase(String email, String normalizedEmail) {
		User user = userRepository.findByEmail(normalizedEmail).orElse(null);
		if (user == null && email != null && !email.trim().equals(normalizedEmail)) {
			user = userRepository.findByEmail(email.trim()).orElse(null);
		}
		return user;
	}

	private void syncFollowCounters(Set<Long> userIds) {
		userIds.stream().filter(id -> id != null).distinct().forEach(id -> userRepository.findById(id).ifPresent(user -> {
			user.setFollowers((int) Math.max(0, followRepository.countByFollowingId(id)));
			user.setFollowing((int) Math.max(0, followRepository.countByFollowerId(id)));
			userRepository.save(user);
		}));
	}

	private String msg(String code, String defaultMessage) {
		return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
	}
}
