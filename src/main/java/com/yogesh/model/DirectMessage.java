package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "direct_messages", indexes = { @Index(name = "idx_dm_sender", columnList = "sender_id"),
		@Index(name = "idx_dm_receiver", columnList = "receiver_id"),
		@Index(name = "idx_dm_created", columnList = "created_at"),
		@Index(name = "idx_dm_unread", columnList = "receiver_id,is_read") })
public class DirectMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	@Column(name = "receiver_id", nullable = false)
	private Long receiverId;

	@Column(nullable = true, length = 1000)
	private String text;

	@Column(name = "image_url")
	private String imageUrl;

	@Column(name = "is_read", nullable = false)
	private boolean read = false;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

}