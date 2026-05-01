package com.yogesh.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {

	private Long id;
	private String type;

	private Long actorId;
	private String actorName;
	private String actorAvatar;
	private String message;

	private String fromUserName;

	private Long postId;
	private String thumbnail;
	private String postThumbnail;

	private String commentText;
	private boolean read;
	private LocalDateTime createdAt;
}
