package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String token;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private boolean used = false;

	@PrePersist
	public void prePersist() {
		if (expiresAt == null)
			expiresAt = LocalDateTime.now().plusMinutes(30); // 30 min valid
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}
}