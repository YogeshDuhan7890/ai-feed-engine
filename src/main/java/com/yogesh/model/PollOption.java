package com.yogesh.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(
        name = "poll_options",
        indexes = {
                @Index(name = "idx_poll_option_poll_id", columnList = "poll_id")
        }
)
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "text", length = 140, nullable = false)
    private String text;
}

