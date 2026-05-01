package com.yogesh.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentDTO {
	private Long id;
	private Long postId;
	private Long userId;
	private String userName;
	private String userAvatar;
	private String text;
	private Long parentId;
	private int likeCount;
	private boolean likedByMe;
	private LocalDateTime createdAt;

	private List<CommentDTO> replies;
}