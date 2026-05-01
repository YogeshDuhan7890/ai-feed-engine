package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "poll_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"poll_id", "user_id"}),
        indexes = {
                @Index(name = "idx_poll_vote_poll_id", columnList = "poll_id"),
                @Index(name = "idx_poll_vote_user_id", columnList = "user_id"),
                @Index(name = "idx_poll_vote_option_id", columnList = "option_id")
        }
)
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}

