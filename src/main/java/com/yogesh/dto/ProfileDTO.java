package com.yogesh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfileDTO {

	private Long id;
	private String name;
	private String email;
	private String username;
	private String bio;
	private String avatar;
	private String coverUrl;
	private String websiteUrl;

	private String[] interests;
	private String role;

	private boolean verified;
	private boolean isMe;
	private boolean isFollowing;
	private boolean isBlocked;

	private int followersCount;
	private int followingCount;

	private long videos;
	private LocalDateTime joinedAt;
}