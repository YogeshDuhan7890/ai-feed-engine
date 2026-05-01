package com.yogesh.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EngagementEvent {

    private Long userId;
    private Long postId;
    private String type;
    private Integer watchTime;
}
