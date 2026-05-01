package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "close_friends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "friend_user_id"}),
        indexes = {
                @Index(name = "idx_close_friend_user", columnList = "user_id"),
                @Index(name = "idx_close_friend_friend", columnList = "friend_user_id")
        }
)
public class CloseFriend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // owner user
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // friend who can see CLOSE_FRIENDS stories
    @Column(name = "friend_user_id", nullable = false)
    private Long friendUserId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}

