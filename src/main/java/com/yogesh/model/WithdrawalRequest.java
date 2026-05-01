package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "amount", precision = 10, scale = 2, nullable = false)
	private BigDecimal amount;

	@Column(name = "upi_id", nullable = false)
	private String upiId;

	// PENDING / APPROVED / REJECTED / COMPLETED
	@Column(name = "status")
	private String status = "PENDING";

	@Column(name = "admin_note")
	private String adminNote;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "processed_at")
	private LocalDateTime processedAt;
}