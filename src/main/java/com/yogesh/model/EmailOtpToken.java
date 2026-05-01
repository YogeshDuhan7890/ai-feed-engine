package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_otp_tokens")
@Data
public class EmailOtpToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, length = 6)
	private String otp; // 6-digit OTP

	// Purpose: VERIFY_EMAIL / CHANGE_EMAIL / TWO_FA / LOGIN
	@Column(nullable = false)
	private String purpose;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private boolean used = false;

	// Failed attempts (5 attempts ke baad block)
	@Column(name = "attempts", nullable = false)
	private int attempts = 0;

	@PrePersist
	public void prePersist() {
		if (expiresAt == null)
			expiresAt = LocalDateTime.now().plusMinutes(5); // 5 min
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}

	public boolean isBlocked() {
		return attempts >= 5;
	}
}
