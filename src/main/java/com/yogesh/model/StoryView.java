package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "story_views",
       uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "viewer_id"}))
public class StoryView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_id", nullable = false)
    private Long storyId;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    @Column(name = "viewed_at")
    private LocalDateTime viewedAt = LocalDateTime.now();
}