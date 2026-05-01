package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Entity
@SQLDelete(sql = "UPDATE users SET is_deleted = true, enabled = false WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
@Table(name = "users", indexes = { @Index(name = "idx_user_email", columnList = "email", unique = true),
		@Index(name = "idx_user_role", columnList = "role"),
		@Index(name = "idx_user_is_deleted", columnList = "is_deleted") })
@Data
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;
	private String email;

	@Column(columnDefinition = "text[]")
	private String[] interests;

	private String password;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(nullable = false)
	private String role = "USER";

	private String avatar;

	@Column(length = 300)
	private String bio;

	@Column(name = "website_url", length = 200)
	private String websiteUrl;

	@Column(name = "username", length = 50, unique = true)
	private String username;

	@Column(name = "is_verified")
	private boolean verified = false;

	@Column(name = "cover_url", length = 500)
	private String coverUrl;

	private int followers;
	private int following;

	@Column(name = "email_verified")
	private boolean emailVerified = false;

	@Column(name = "pending_email", length = 255)
	private String pendingEmail;

	@Column(name = "two_fa_enabled")
	private boolean twoFaEnabled = false;

	@Column(name = "two_fa_secret", length = 255)
	private String twoFaSecret;

	@Column(name = "private_account")
	private Boolean privateAccount = false;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@PrePersist
	public void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
		if (privateAccount == null)
			privateAccount = false;
	}

	public boolean isPrivateAccount() {
		return Boolean.TRUE.equals(privateAccount);
	}
}
