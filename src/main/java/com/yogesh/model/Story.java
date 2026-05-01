package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Data
@Entity
@SQLDelete(sql = "UPDATE stories SET is_deleted = true WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
@Table(name = "stories", indexes = { @Index(name = "idx_story_is_deleted", columnList = "is_deleted") })
public class Story {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "media_url", nullable = false)
	private String mediaUrl;

	// "IMAGE" or "VIDEO"
	@Column(name = "media_type")
	private String mediaType = "IMAGE";

	@Column(length = 300)
	private String caption;

	// Text overlay color (hex)
	@Column(name = "text_color", length = 10)
	private String textColor = "#ffffff";

	// Background color/gradient for text stories
	@Column(name = "bg_color", length = 50)
	private String bgColor;

	// Story type: MEDIA / TEXT / BOOMERANG
	@Column(name = "story_type", length = 20)
	private String storyType = "MEDIA";

	// Reaction counts (emoji → count stored as JSON string)
	@Column(name = "reactions", length = 500)
	private String reactions;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "view_count")
	private int viewCount = 0;

	@Column(name = "reply_count")
	private int replyCount = 0;

	// Privacy: PUBLIC, FOLLOWERS, CLOSE_FRIENDS
	@Column(name = "privacy", length = 20)
	private String privacy = "PUBLIC";

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@PrePersist
	public void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
		if (expiresAt == null)
			expiresAt = LocalDateTime.now().plusHours(24);
	}
}
