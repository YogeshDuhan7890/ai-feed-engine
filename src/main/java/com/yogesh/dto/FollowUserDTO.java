package com.yogesh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowUserDTO {

	private Long id;
	private String name;
	private String username;
	private String avatar;
	private String bio;
	private boolean verified;
	private int followers;

	private boolean isFollowing;
}