package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "live_streams")
public class LiveStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "stream_key", unique = true, nullable = false)
    private String streamKey;

    // LIVE / ENDED
    @Column(name = "status", nullable = false)
    private String status = "LIVE";

    @Column(name = "viewer_count")
    private int viewerCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "thumbnail")
    private String thumbnail;
}