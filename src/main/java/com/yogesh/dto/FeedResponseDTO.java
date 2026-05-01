package com.yogesh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FeedResponseDTO — Feed mein ek post ka response format.
 *
 * BUG FIX: Yeh file pehle completely EMPTY thi. FeedPageResponse.java is DTO ko
 * use karta hai: private List<FeedResponseDTO> feed; Empty class hone ki wajah
 * se saare feed responses null/empty aa rahe the.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedResponseDTO {

	private Long postId;
	private String videoUrl;
	private String content; 
	private String tags;

	private Long userId;
	private String userName;
	private String userAvatar;

	private Long likes;
	private Long likeCount;
	private Long viewCount;
	private Long comments;
	private Long shares;

	private boolean likedByMe;
	private boolean bookmarkedByMe;

	private Double score;
	private LocalDateTime createdAt;
}
