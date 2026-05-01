package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "push_subscriptions", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = { "user_id",
		"endpoint" }))
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 500)
	private String endpoint;

	@Column(name = "p256dh", nullable = false, length = 200)
	private String p256dh;

	@Column(name = "auth_key", nullable = false, length = 100)
	private String authKey;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();
}