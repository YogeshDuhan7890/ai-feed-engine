package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reports")
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	// What is being reported — USER or POST
	@Column(name = "target_type", nullable = false, length = 10)
	private String targetType; // "USER" or "POST"

	@Column(name = "target_id", nullable = false)
	private Long targetId;

	// Reason category
	@Column(name = "reason", nullable = false, length = 50)
	private String reason; // SPAM, HATE, VIOLENCE, NUDITY, HARASSMENT, MISINFORMATION, OTHER

	@Column(name = "description", length = 500)
	private String description;

	// Status: PENDING, REVIEWED, RESOLVED, DISMISSED
	@Column(name = "status", length = 20)
	private String status = "PENDING";

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

	@PrePersist
	public void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
		if (status == null)
			status = "PENDING";
	}
}