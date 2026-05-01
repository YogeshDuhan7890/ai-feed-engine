package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens")
@Data
public class EmailVerificationToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String token;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "used", nullable = false)
	private boolean used = false;

	@PrePersist
	public void prePersist() {
		if (expiresAt == null) {
			expiresAt = LocalDateTime.now().plusHours(24);
		}
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}
}