package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "verification_requests",
        indexes = {
                @Index(name = "idx_ver_req_user_id", columnList = "user_id"),
                @Index(name = "idx_ver_req_status", columnList = "status"),
                @Index(name = "idx_ver_req_created_at", columnList = "created_at DESC")
        },
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"})
)
public class VerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // PENDING | APPROVED | REJECTED
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "category", length = 60)
    private String category; // e.g. CREATOR / BUSINESS / PUBLIC_FIGURE

    @Column(name = "govt_id_type", length = 40)
    private String govtIdType; // e.g. AADHAAR / PAN / PASSPORT

    @Column(name = "govt_id_last4", length = 10)
    private String govtIdLast4;

    @Column(name = "social_links", length = 1200)
    private String socialLinks; // comma/newline separated

    @Column(name = "notes", length = 1200)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by", length = 255)
    private String decidedBy; // admin email

    @Column(name = "decision_reason", length = 800)
    private String decisionReason;
}

