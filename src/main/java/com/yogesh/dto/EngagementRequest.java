package com.yogesh.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EngagementRequest {
	
    private Long userId;
	
	@NotNull
    private Long postId;
	
	@NotNull
	private String type;     // LIKE, COMMENT, SHARE, WATCH
    private Integer watchTime;
    private String commentText;
}