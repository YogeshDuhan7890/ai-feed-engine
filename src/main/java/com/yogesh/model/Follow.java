package com.yogesh.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Table(name = "follows", uniqueConstraints = @UniqueConstraint(columnNames = { "follower_id",
		"following_id" }), indexes = { @Index(name = "idx_follow_follower", columnList = "follower_id"),
				@Index(name = "idx_follow_following", columnList = "following_id") })
@Data
public class Follow {

	public static final String STATUS_ACCEPTED = "ACCEPTED";
	public static final String STATUS_PENDING = "PENDING";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long followerId;

	private Long followingId;

	private LocalDateTime createdAt;

	private String status;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null || status.isBlank()) {
			status = STATUS_ACCEPTED;
		}
	}

}
