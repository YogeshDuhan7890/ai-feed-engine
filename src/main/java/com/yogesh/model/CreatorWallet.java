package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "creator_wallets")
public class CreatorWallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", unique = true, nullable = false)
	private Long userId;

	// Total earnings (INR)
	@Column(name = "total_earnings", precision = 10, scale = 2)
	private BigDecimal totalEarnings = BigDecimal.ZERO;

	// Available for withdrawal
	@Column(name = "available_balance", precision = 10, scale = 2)
	private BigDecimal availableBalance = BigDecimal.ZERO;

	// Withdrawn so far
	@Column(name = "total_withdrawn", precision = 10, scale = 2)
	private BigDecimal totalWithdrawn = BigDecimal.ZERO;

	// Monetization enabled?
	@Column(name = "monetization_enabled")
	private boolean monetizationEnabled = false;

	// Creator tier: NONE / RISING / PRO / ELITE
	@Column(name = "tier")
	private String tier = "NONE";

	// UPI ID for withdrawal
	@Column(name = "upi_id")
	private String upiId;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "updated_at")
	private LocalDateTime updatedAt = LocalDateTime.now();
}