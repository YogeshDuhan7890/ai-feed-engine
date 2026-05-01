package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Entity
@SQLDelete(sql = "UPDATE comments SET is_deleted = true WHERE id = ?")
@SQLRestriction("(is_deleted = false OR is_deleted IS NULL)")
@Table(name = "comments", indexes = { @Index(name = "idx_comment_is_deleted", columnList = "is_deleted") })
@Data
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long postId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 1000)
	private String text;

	// Reply support — parent comment ka ID
	@Column(name = "parent_id")
	private Long parentId;

	// Like count
	@Column(name = "like_count")
	private Integer likeCount = 0;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Column(name = "is_deleted")
	private boolean isDeleted = false;

	@PrePersist
	public void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
	}
}
