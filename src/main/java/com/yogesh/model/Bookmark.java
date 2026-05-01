package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bookmarks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    // Optional folder / collection name (e.g. "Comedy", "Tech")
    @Column(name = "collection_name", length = 120)
    private String collectionName;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}