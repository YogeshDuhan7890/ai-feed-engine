package com.yogesh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultDTO {

	private String query;
	private List<UserSearchDTO> users;
	private List<PostSearchDTO> posts;

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class UserSearchDTO {
		private Long id;
		private String name;
		private String email;
		private String avatar;
		private int followers;

		private boolean following;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class PostSearchDTO {
		private Long id;
		private String videoUrl;
		private String content;
		private String tags;
		private Long userId;
		private String userName;
		private String userAvatar;
		private long likes;
	}
}