package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "earning_transactions")
public class EarningTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "post_id")
	private Long postId;

	// VIEW_REWARD / LIKE_BONUS / SHARE_BONUS / MILESTONE_BONUS / WITHDRAWAL /
	// SUBSCRIPTION
	@Column(name = "type", nullable = false)
	private String type;

	@Column(name = "amount", precision = 8, scale = 4, nullable = false)
	private BigDecimal amount;

	@Column(name = "description")
	private String description;

	@Column(name = "external_txn_id", unique = true, length = 120)
	private String externalTxnId;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();
}
