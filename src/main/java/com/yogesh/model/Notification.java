package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = { @Index(name = "idx_notif_to_user", columnList = "to_user_id"),
		@Index(name = "idx_notif_created", columnList = "to_user_id, created_at DESC") })
@Data
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "to_user_id", nullable = false)
	private Long toUserId;

	@Column(name = "from_user_id")
	private Long fromUserId;

	private String type;

	@Column(name = "post_id")
	private Long postId;

	@Column(length = 500, name = "comment_text")
	private String commentText;

	@Column(nullable = false)
	private boolean read = false;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		createdAt = LocalDateTime.now();
	}
}