package com.yogesh.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Data
@Entity
@SQLDelete(sql = "UPDATE posts SET is_deleted = true WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
@Table(name = "posts", indexes = { @Index(name = "idx_post_user_id", columnList = "user_id"),
		@Index(name = "idx_post_created_at", columnList = "created_at DESC"),
		@Index(name = "idx_post_scheduled_at", columnList = "scheduled_at"),
		@Index(name = "idx_post_tags", columnList = "tags"),
		@Index(name = "idx_post_is_deleted", columnList = "is_deleted"),
		@Index(name = "idx_post_parent_post_id", columnList = "parent_post_id") })
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 2000)
	private String content;

	@Column(name = "video_url")
	private String videoUrl;

	@Column(name = "thumbnail_url")
	private String thumbnailUrl;

	@Column(length = 500)
	private String tags;

	@Column(columnDefinition = "TEXT")
	private String embedding;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "scheduled_at")
	private LocalDateTime scheduledAt;

	@Column(length = 20, nullable = true)
	private String status = "PUBLISHED"; // PUBLISHED | SCHEDULED

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "duration")
	private Double duration;

	@Column(columnDefinition = "TEXT")
	private String chapters; // JSON string: [{ "timeSeconds": 0, "title": "Intro" }, ...]

	// Duet / response: original post id (nullable)
	@Column(name = "parent_post_id")
	private Long parentPostId;

	@Column(name = "is_pinned")
	@JsonProperty("isPinned")
	private boolean isPinned = false;

	@Column(name = "is_deleted")
	@JsonProperty("isDeleted")
	private boolean isDeleted = false;

	@Column(name = "like_count", nullable = false, columnDefinition = "bigint default 0")
	private Long likeCount = 0L;

	@Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
	private Long viewCount = 0L;

	@PrePersist
	public void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
		if (status == null || status.isBlank())
			status = "PUBLISHED";
		if (likeCount == null)
			likeCount = 0L;
		if (viewCount == null)
			viewCount = 0L;
	}

	@PostLoad
	public void postLoad() {
		if (status == null || status.isBlank())
			status = "PUBLISHED";
		if (likeCount == null)
			likeCount = 0L;
		if (viewCount == null)
			viewCount = 0L;
	}
}
