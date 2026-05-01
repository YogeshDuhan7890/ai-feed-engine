package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "post_hashtags",
       uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "hashtag_id"}))
public class PostHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "hashtag_id", nullable = false)
    private Long hashtagId;
}