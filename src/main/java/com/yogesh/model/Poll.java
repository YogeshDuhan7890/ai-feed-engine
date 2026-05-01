package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "polls",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id"}),
        indexes = {
                @Index(name = "idx_poll_post_id", columnList = "post_id"),
                @Index(name = "idx_poll_expires_at", columnList = "expires_at")
        }
)
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "question", length = 280)
    private String question;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}

